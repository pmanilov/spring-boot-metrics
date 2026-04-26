package com.manilov.common.mqttquic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared MQTT-over-QUIC transport used both by the experiment client and the
 * metrics subscriber service.
 */
public final class MqttQuicClient implements AutoCloseable {

    private static final int MAX_MQTT_PACKET_SIZE = 1_048_576;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration STREAM_OPEN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 5;
    private static final int RECEIVED_QOS2_CACHE_LIMIT = 4096;

    private final NioEventLoopGroup group;
    private final Channel udpChannel;
    private final QuicSslContext sslContext;
    private final InetSocketAddress remote;

    public MqttQuicClient(String host, int port, String alpn) throws Exception {
        this.remote = resolveRemote(host, port);
        this.sslContext = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(alpn)
                .earlyData(true)
                .build();

        this.group = new NioEventLoopGroup(1);

        ChannelHandler codec = new QuicClientCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(60, TimeUnit.SECONDS)
                .initialMaxData(100_000_000)
                .initialMaxStreamDataBidirectionalLocal(10_000_000)
                .initialMaxStreamDataBidirectionalRemote(10_000_000)
                .initialMaxStreamDataUnidirectional(10_000_000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .build();

        this.udpChannel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0)
                .sync()
                .channel();
    }

    public Session openSession(String clientId) throws Exception {
        QuicChannel quicChannel = QuicChannel.newBootstrap(udpChannel)
                .streamHandler(new ChannelInboundHandlerAdapter())
                .remoteAddress(remote)
                .connect()
                .get();

        waitForPeerStreamAllowance(quicChannel, QuicStreamType.BIDIRECTIONAL, STREAM_OPEN_TIMEOUT);

        SessionState state = new SessionState();
        QuicStreamChannel stream = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(MqttEncoder.INSTANCE);
                        ch.pipeline().addLast(new MqttDecoder(MAX_MQTT_PACKET_SIZE));
                        ch.pipeline().addLast(new SessionHandler(state));
                    }
                }).get();

        Session session = new Session(quicChannel, stream, state);
        state.bind(session);

        MqttMessage connect = MqttMessageBuilders.connect()
                .protocolVersion(MqttVersion.MQTT_3_1_1)
                .clientId(clientId)
                .cleanSession(true)
                .keepAlive(60)
                .build();
        session.writeAndFlush(connect);

        try {
            state.connAckFuture.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            session.close();
            throw new IllegalStateException("Timed out waiting for CONNACK", e);
        } catch (ExecutionException e) {
            session.close();
            throw unwrap("Failed to establish MQTT-over-QUIC session", e);
        }

        return session;
    }

    private static InetSocketAddress resolveRemote(String host, int port) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(host);
        return new InetSocketAddress(address, port);
    }

    private static void waitForPeerStreamAllowance(QuicChannel quicChannel, QuicStreamType type, Duration timeout)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (quicChannel.isOpen()) {
            if (quicChannel.peerAllowedStreams(type) > 0) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new IllegalStateException("Timed out waiting for peer stream allowance for " + type);
            }
            Thread.sleep(10);
        }
        throw new IllegalStateException("QUIC connection closed before peer granted stream allowance for " + type);
    }

    @Override
    public void close() {
        try {
            udpChannel.close().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static final class Session implements AutoCloseable {
        private final QuicChannel quicChannel;
        private final QuicStreamChannel stream;
        private final SessionState state;
        private final AtomicInteger nextPacketId = new AtomicInteger(1);

        private Session(QuicChannel quicChannel, QuicStreamChannel stream, SessionState state) {
            this.quicChannel = quicChannel;
            this.stream = stream;
            this.state = state;
        }

        public void setMessageListener(MessageListener listener) {
            state.messageListener = listener != null ? listener : MessageListener.NOOP;
        }

        public CompletableFuture<Void> closedFuture() {
            return state.closedFuture;
        }

        public void publish(String topic, byte[] payload) {
            publish(topic, payload, MqttQoS.AT_MOST_ONCE);
        }

        public void publish(String topic, byte[] payload, MqttQoS qos) {
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(qos, "qos");

            if (qos == MqttQoS.AT_MOST_ONCE) {
                writeAndFlush(buildPublish(topic, payload, qos, 0, false));
                return;
            }

            int packetId = nextPacketId();
            InFlight inFlight = new InFlight(packetId, topic, payload.clone(), qos);
            state.inFlights.put(packetId, inFlight);
            writeAndFlush(buildPublish(topic, inFlight.payload, qos, packetId, false));

            try {
                awaitCompletionWithRetry(inFlight);
            } finally {
                state.inFlights.remove(packetId);
            }
        }

        public void subscribe(String topic, MqttQoS qos) {
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(qos, "qos");

            int packetId = nextPacketId();
            CompletableFuture<Integer> subAckFuture = new CompletableFuture<>();
            state.subAckFutures.put(packetId, subAckFuture);
            writeAndFlush(MqttMessageBuilders.subscribe()
                    .messageId(packetId)
                    .addSubscription(qos, topic)
                    .build());

            try {
                int grantedQos = subAckFuture.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                if (grantedQos == MqttQoS.FAILURE.value()) {
                    throw new IllegalStateException("Subscription to '" + topic + "' was rejected");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for SUBACK", e);
            } catch (ExecutionException e) {
                throw unwrap("Failed while waiting for SUBACK", e);
            } catch (TimeoutException e) {
                throw new IllegalStateException("Timed out waiting for SUBACK", e);
            } finally {
                state.subAckFutures.remove(packetId);
            }
        }

        public void ping() {
            writeAndFlush(MqttMessage.PINGREQ);
        }

        @Override
        public void close() {
            state.closing = true;
            try {
                writeAndFlush(MqttMessage.DISCONNECT);
            } catch (RuntimeException ignored) {
            }
            quicChannel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        }

        private int nextPacketId() {
            for (int i = 0; i < 65_535; i++) {
                int candidate = nextPacketId.getAndUpdate(current -> current >= 65_535 ? 1 : current + 1);
                if (!state.inFlights.containsKey(candidate) && !state.subAckFutures.containsKey(candidate)) {
                    return candidate;
                }
            }
            throw new IllegalStateException("No MQTT packet identifiers available");
        }

        private void awaitCompletionWithRetry(InFlight inFlight) {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    inFlight.completion.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for MQTT ACK", e);
                } catch (ExecutionException e) {
                    throw unwrap("Failed while waiting for MQTT ACK", e);
                } catch (TimeoutException e) {
                    if (attempt == MAX_RETRIES) {
                        throw new IllegalStateException(
                                "Timed out waiting for MQTT ACK for packet " + inFlight.packetId, e);
                    }
                    retransmit(inFlight);
                }
            }
        }

        private void retransmit(InFlight inFlight) {
            InFlightState currentState = inFlight.state.get();
            if (currentState == InFlightState.WAIT_PUBCOMP) {
                writeAndFlush(buildPubRel(inFlight.packetId, true));
            } else {
                writeAndFlush(buildPublish(inFlight.topic, inFlight.payload, inFlight.qos, inFlight.packetId, true));
            }
        }

        private void writeAndFlush(MqttMessage message) {
            ChannelFuture future = stream.writeAndFlush(message);
            if (stream.eventLoop().inEventLoop()) {
                future.addListener(writeFuture -> {
                    if (!writeFuture.isSuccess()) {
                        fail(writeFuture.cause() != null
                                ? writeFuture.cause()
                                : new IllegalStateException("Failed to write MQTT packet"));
                    }
                });
                return;
            }
            boolean completed = future.awaitUninterruptibly(WRITE_TIMEOUT.toMillis());
            if (!completed) {
                throw new IllegalStateException("Timed out while writing MQTT packet");
            }
            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                throw new IllegalStateException("Failed to write MQTT packet", cause);
            }
        }

        private void handlePubAck(int packetId) {
            InFlight inFlight = state.inFlights.remove(packetId);
            if (inFlight != null) {
                inFlight.completion.complete(null);
            }
        }

        private void handlePubRec(int packetId) {
            InFlight inFlight = state.inFlights.get(packetId);
            if (inFlight == null) {
                return;
            }
            if (inFlight.state.compareAndSet(InFlightState.WAIT_PUBREC, InFlightState.WAIT_PUBCOMP)) {
                writeAndFlush(buildPubRel(packetId, false));
            }
        }

        private void handlePubComp(int packetId) {
            InFlight inFlight = state.inFlights.remove(packetId);
            if (inFlight != null) {
                inFlight.completion.complete(null);
            }
        }

        private void handleIncomingPublish(MqttPublishMessage publish) {
            String topic = publish.variableHeader().topicName();
            MqttQoS qos = publish.fixedHeader().qosLevel();
            int packetId = publish.variableHeader().packetId();
            byte[] payload = new byte[publish.payload().readableBytes()];
            publish.payload().getBytes(publish.payload().readerIndex(), payload);

            if (qos == MqttQoS.EXACTLY_ONCE) {
                if (!state.completedIncomingQos2.containsKey(packetId)) {
                    state.pendingIncomingQos2.putIfAbsent(packetId, new PendingInboundMessage(topic, payload));
                }
                writeAndFlush(buildSimpleAck(MqttMessageType.PUBREC, packetId));
                return;
            }

            dispatchMessage(topic, payload, qos, publish.fixedHeader().isDup(), packetId);
            if (qos == MqttQoS.AT_LEAST_ONCE) {
                writeAndFlush(buildSimpleAck(MqttMessageType.PUBACK, packetId));
            }
        }

        private void handleIncomingPubRel(int packetId) {
            PendingInboundMessage pending = state.pendingIncomingQos2.remove(packetId);
            if (pending != null) {
                if (state.completedIncomingQos2.size() >= RECEIVED_QOS2_CACHE_LIMIT) {
                    state.completedIncomingQos2.clear();
                }
                state.completedIncomingQos2.put(packetId, Boolean.TRUE);
                dispatchMessage(pending.topic, pending.payload, MqttQoS.EXACTLY_ONCE, false, packetId);
            }
            if (pending != null || state.completedIncomingQos2.containsKey(packetId)) {
                writeAndFlush(buildSimpleAck(MqttMessageType.PUBCOMP, packetId));
            }
        }

        private void dispatchMessage(String topic, byte[] payload, MqttQoS qos, boolean duplicate, int packetId) {
            state.messageListener.onMessage(topic, payload, qos, duplicate, packetId);
        }

        private void fail(Throwable cause) {
            state.failAll(cause);
        }
    }

    @FunctionalInterface
    public interface MessageListener {
        MessageListener NOOP = (topic, payload, qos, duplicate, packetId) -> { };

        void onMessage(String topic, byte[] payload, MqttQoS qos, boolean duplicate, int packetId);
    }

    private static final class SessionState {
        private final CompletableFuture<Void> connAckFuture = new CompletableFuture<>();
        private final CompletableFuture<Void> closedFuture = new CompletableFuture<>();
        private final Map<Integer, InFlight> inFlights = new ConcurrentHashMap<>();
        private final Map<Integer, CompletableFuture<Integer>> subAckFutures = new ConcurrentHashMap<>();
        private final Map<Integer, PendingInboundMessage> pendingIncomingQos2 = new ConcurrentHashMap<>();
        private final Map<Integer, Boolean> completedIncomingQos2 = new ConcurrentHashMap<>();
        private volatile MessageListener messageListener = MessageListener.NOOP;
        private volatile Session session;
        private volatile boolean closing;

        private void bind(Session session) {
            this.session = session;
        }

        private void failAll(Throwable cause) {
            if (!connAckFuture.isDone()) {
                connAckFuture.completeExceptionally(cause);
            }
            inFlights.values().forEach(inFlight -> inFlight.completion.completeExceptionally(cause));
            subAckFutures.values().forEach(future -> future.completeExceptionally(cause));
            if (!closedFuture.isDone()) {
                if (closing) {
                    closedFuture.complete(null);
                } else {
                    closedFuture.completeExceptionally(cause);
                }
            }
        }
    }

    private static final class SessionHandler extends SimpleChannelInboundHandler<MqttMessage> {
        private final SessionState state;

        private SessionHandler(SessionState state) {
            this.state = state;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
            if (msg.decoderResult().isFailure()) {
                Throwable cause = msg.decoderResult().cause();
                state.session.fail(cause != null ? cause : new IllegalStateException("Malformed MQTT packet"));
                ctx.close();
                return;
            }

            switch (msg.fixedHeader().messageType()) {
                case CONNACK -> handleConnAck((MqttConnAckMessage) msg);
                case PUBACK -> state.session.handlePubAck(((MqttPubAckMessage) msg).variableHeader().messageId());
                case PUBREC -> state.session.handlePubRec(packetId(msg));
                case PUBCOMP -> state.session.handlePubComp(packetId(msg));
                case SUBACK -> handleSubAck((MqttSubAckMessage) msg);
                case PUBLISH -> state.session.handleIncomingPublish((MqttPublishMessage) msg);
                case PUBREL -> state.session.handleIncomingPubRel(packetId(msg));
                case PINGRESP, DISCONNECT -> { }
                default -> { }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            state.failAll(new IllegalStateException("MQTT-over-QUIC stream closed"));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            state.failAll(cause);
            ctx.close();
        }

        private void handleConnAck(MqttConnAckMessage ack) {
            if (ack.variableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                state.connAckFuture.complete(null);
            } else {
                state.connAckFuture.completeExceptionally(new IllegalStateException(
                        "CONNACK rejected: " + ack.variableHeader().connectReturnCode()));
            }
        }

        private void handleSubAck(MqttSubAckMessage subAck) {
            CompletableFuture<Integer> future = state.subAckFutures.remove(subAck.variableHeader().messageId());
            if (future == null) {
                return;
            }
            if (subAck.payload().grantedQoSLevels().isEmpty()) {
                future.completeExceptionally(new IllegalStateException("SUBACK payload is empty"));
                return;
            }
            int grantedQos = subAck.payload().grantedQoSLevels().getFirst();
            if (grantedQos == MqttQoS.FAILURE.value()) {
                future.completeExceptionally(new IllegalStateException("Subscription rejected by broker"));
                return;
            }
            future.complete(grantedQos);
        }

        private int packetId(MqttMessage message) {
            return ((MqttMessageIdVariableHeader) message.variableHeader()).messageId();
        }
    }

    private static final class InFlight {
        private final int packetId;
        private final String topic;
        private final byte[] payload;
        private final MqttQoS qos;
        private final AtomicReference<InFlightState> state;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        private InFlight(int packetId, String topic, byte[] payload, MqttQoS qos) {
            this.packetId = packetId;
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
            this.state = new AtomicReference<>(
                    qos == MqttQoS.EXACTLY_ONCE ? InFlightState.WAIT_PUBREC : InFlightState.WAIT_PUBACK);
        }
    }

    private enum InFlightState {
        WAIT_PUBACK,
        WAIT_PUBREC,
        WAIT_PUBCOMP
    }

    private record PendingInboundMessage(String topic, byte[] payload) { }

    private static MqttPublishMessage buildPublish(String topic, byte[] payload, MqttQoS qos, int packetId, boolean dup) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.PUBLISH, dup, qos, false, 0);
        MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, packetId);
        return new MqttPublishMessage(fixedHeader, variableHeader,
                Unpooled.copiedBuffer(payload));
    }

    private static MqttMessage buildSimpleAck(MqttMessageType messageType, int packetId) {
        MqttQoS qos = messageType == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE;
        return new MqttMessage(new MqttFixedHeader(messageType, false, qos, false, 0),
                MqttMessageIdVariableHeader.from(packetId));
    }

    private static MqttMessage buildPubRel(int packetId, boolean dup) {
        return new MqttMessage(new MqttFixedHeader(MqttMessageType.PUBREL, dup, MqttQoS.AT_LEAST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(packetId));
    }

    private static IllegalStateException unwrap(String message, ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return new IllegalStateException(message, cause);
    }
}
