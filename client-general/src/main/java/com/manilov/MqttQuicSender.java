package com.manilov;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
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
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.incubator.codec.quic.QuicStreamType;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MQTT-over-QUIC client (canonical mapping: one QUIC connection per MQTT
 * session). All MQTT control packets travel on a single bidirectional stream
 * inside that connection, matching EMQX's "MQTT over QUIC" transport.
 *
 * <p>A single shared {@link NioEventLoopGroup} + UDP socket + client codec
 * are reused across sessions — that is just a Netty-level multiplexing detail
 * and does <em>not</em> merge the QUIC connections themselves (each connection
 * still has its own TLS handshake, congestion window and flow-control state).
 *
 * <p>A TLS session cache is enabled on the shared SSL context so that a QUIC
 * client that reconnects to the same server can resume the session (0-RTT
 * capable when the server sends a NEW_TOKEN / session ticket).
 */
public final class MqttQuicSender {

    private static volatile MqttQuicSender INSTANCE;

    private final NioEventLoopGroup group;
    private final Channel udpChannel;
    private final QuicSslContext sslContext;
    private final InetSocketAddress remote;

    private MqttQuicSender(String host, int port, String alpn) throws Exception {
        this.remote = new InetSocketAddress(host, port);
        this.sslContext = QuicSslContextBuilder.forClient()
                .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
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
                .bind(0).sync().channel();
    }

    public static MqttQuicSender get() {
        MqttQuicSender local = INSTANCE;
        if (local != null) return local;
        synchronized (MqttQuicSender.class) {
            if (INSTANCE == null) {
                try {
                    INSTANCE = new MqttQuicSender(Config.quicHost, Config.quicPort, Config.quicAlpn);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize QUIC transport", e);
                }
            }
            return INSTANCE;
        }
    }

    public static synchronized void close() {
        if (INSTANCE != null) {
            try {
                INSTANCE.udpChannel.close().sync();
                INSTANCE.group.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            INSTANCE = null;
        }
    }

    /**
     * Opens a dedicated QUIC connection + MQTT session for one logical client.
     * The returned {@link Session} owns its own QuicChannel (distinct TLS
     * handshake, distinct congestion control) — closing it tears the whole
     * QUIC connection down.
     */
    public Session openSession(String clientId) throws Exception {
        QuicChannel quicChannel = QuicChannel.newBootstrap(udpChannel)
                .streamHandler(new ChannelInboundHandlerAdapter())
                .remoteAddress(remote)
                .connect()
                .get();

        CompletableFuture<Void> connAckFuture = new CompletableFuture<>();
        QuicStreamChannel stream = quicChannel.createStream(
                QuicStreamType.BIDIRECTIONAL,
                new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(MqttEncoder.INSTANCE);
                        ch.pipeline().addLast(new MqttDecoder());
                        ch.pipeline().addLast(new ConnAckHandler(connAckFuture));
                    }
                }).get();

        MqttMessage connect = MqttMessageBuilders.connect()
                .protocolVersion(MqttVersion.MQTT_3_1_1)
                .clientId(clientId)
                .cleanSession(true)
                .keepAlive(60)
                .build();
        stream.writeAndFlush(connect);

        try {
            connAckFuture.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            quicChannel.close();
            throw new RuntimeException("Timed out waiting for CONNACK", e);
        }
        return new Session(quicChannel, stream);
    }

    /** One MQTT session bound to its own QUIC connection. */
    public static final class Session implements AutoCloseable {
        private final QuicChannel quicChannel;
        private final QuicStreamChannel stream;

        Session(QuicChannel quicChannel, QuicStreamChannel stream) {
            this.quicChannel = quicChannel;
            this.stream = stream;
        }

        public void publish(String topic, byte[] payload) {
            MqttFixedHeader fixedHeader = new MqttFixedHeader(
                    MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0);
            MqttPublishVariableHeader variableHeader = new MqttPublishVariableHeader(topic, 0);
            MqttPublishMessage msg = new MqttPublishMessage(
                    fixedHeader, variableHeader, Unpooled.wrappedBuffer(payload));
            stream.writeAndFlush(msg);
        }

        public void ping() {
            stream.writeAndFlush(new MqttMessage(new MqttFixedHeader(
                    MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0)));
        }

        @Override
        public void close() {
            // Use awaitUninterruptibly so teardown completes even when the
            // publishing virtual thread was interrupted — otherwise pending
            // PUBLISH frames sitting in Netty's outbound buffer would be
            // dropped, inflating the apparent loss ratio.
            MqttMessage disconnect = new MqttMessage(new MqttFixedHeader(
                    MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0));
            try {
                stream.writeAndFlush(disconnect).awaitUninterruptibly(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            quicChannel.close().awaitUninterruptibly(2, TimeUnit.SECONDS);
        }
    }

    private static final class ConnAckHandler extends SimpleChannelInboundHandler<MqttMessage> {
        private final CompletableFuture<Void> future;

        ConnAckHandler(CompletableFuture<Void> future) {
            this.future = future;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
            if (msg.fixedHeader().messageType() == MqttMessageType.CONNACK) {
                MqttConnAckMessage ack = (MqttConnAckMessage) msg;
                if (ack.variableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(new RuntimeException(
                            "CONNACK rejected: " + ack.variableHeader().connectReturnCode()));
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            future.completeExceptionally(cause);
            ctx.close();
        }
    }
}
