package com.manilov.servermqttquic.handler;

import com.manilov.common.service.DelayService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubAckMessage;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal MQTT 3.1.1 broker handler operating on a single QUIC bidirectional
 * stream. Uses Netty's MQTT codec for wire encoding/decoding, so the framing
 * on the stream is byte-for-byte identical to MQTT over TCP — the only
 * difference is the transport underneath (QUIC instead of TCP). This matches
 * the EMQX/IETF "MQTT over QUIC" approach of tunnelling MQTT control packets
 * over a QUIC stream.
 *
 * Supported packets: CONNECT, PUBLISH (QoS 0/1), SUBSCRIBE, UNSUBSCRIBE,
 * PINGREQ, DISCONNECT. PUBREL/PUBREC/PUBCOMP (QoS 2) are not implemented —
 * the experiment clients only use QoS 0.
 */
@Slf4j
public class MqttQuicBrokerHandler extends SimpleChannelInboundHandler<MqttMessage> {

    private final DelayService delayService;
    private final PacketSizeHandler packetSizeHandler;
    private final String serverId;
    private final String metricsTopic;

    public MqttQuicBrokerHandler(DelayService delayService,
                                 PacketSizeHandler packetSizeHandler,
                                 String serverId,
                                 String metricsTopic) {
        this.delayService = delayService;
        this.packetSizeHandler = packetSizeHandler;
        this.serverId = serverId;
        this.metricsTopic = metricsTopic;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MqttMessage msg) {
        if (msg.decoderResult().isFailure()) {
            log.warn("Received malformed MQTT packet: {}", msg.decoderResult().cause().getMessage());
            ctx.close();
            return;
        }

        MqttMessageType type = msg.fixedHeader().messageType();
        switch (type) {
            case CONNECT -> handleConnect(ctx, (MqttConnectMessage) msg);
            case PUBLISH -> handlePublish(ctx, (MqttPublishMessage) msg);
            case SUBSCRIBE -> handleSubscribe(ctx, (MqttSubscribeMessage) msg);
            case UNSUBSCRIBE -> handleUnsubscribe(ctx, (MqttUnsubscribeMessage) msg);
            case PINGREQ -> ctx.writeAndFlush(new MqttMessage(
                    new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0)));
            case DISCONNECT -> ctx.close();
            default -> log.debug("Ignoring unsupported MQTT message type: {}", type);
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, MqttConnectMessage connect) {
        MqttConnAckMessage ack = new MqttConnAckMessage(
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 2),
                new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED, false));
        ctx.writeAndFlush(ack);
    }

    private void handlePublish(ChannelHandlerContext ctx, MqttPublishMessage publish) {
        String topic = publish.variableHeader().topicName();
        ByteBuf payload = publish.payload();
        byte[] bytes = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), bytes);

        if (metricsTopic.equals(topic)) {
            String value = new String(bytes, StandardCharsets.UTF_8);
            int comma = value.indexOf(',');
            String tsStr = comma >= 0 ? value.substring(0, comma) : value;
            try {
                long sentTs = Long.parseLong(tsStr);
                delayService.save(sentTs, serverId);
                packetSizeHandler.handleMessage(topic, bytes);
            } catch (NumberFormatException ignored) {
            }
        }

        MqttQoS qos = publish.fixedHeader().qosLevel();
        if (qos == MqttQoS.AT_LEAST_ONCE) {
            int packetId = publish.variableHeader().packetId();
            MqttPubAckMessage puback = new MqttPubAckMessage(
                    new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 2),
                    MqttMessageIdVariableHeader.from(packetId));
            ctx.writeAndFlush(puback);
        }
    }

    private void handleSubscribe(ChannelHandlerContext ctx, MqttSubscribeMessage subscribe) {
        List<Integer> grantedQos = new ArrayList<>();
        for (MqttTopicSubscription sub : subscribe.payload().topicSubscriptions()) {
            // Accept all subscriptions at the requested QoS (capped at QoS 1).
            int qos = Math.min(sub.qualityOfService().value(), 1);
            grantedQos.add(qos);
        }
        MqttSubAckMessage suback = new MqttSubAckMessage(
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(subscribe.variableHeader().messageId()),
                new MqttSubAckPayload(grantedQos));
        ctx.writeAndFlush(suback);
    }

    private void handleUnsubscribe(ChannelHandlerContext ctx, MqttUnsubscribeMessage unsubscribe) {
        MqttUnsubAckMessage unsuback = new MqttUnsubAckMessage(
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0),
                MqttMessageIdVariableHeader.from(unsubscribe.variableHeader().messageId()));
        ctx.writeAndFlush(unsuback);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("MQTT-QUIC stream error: {}", cause.getMessage());
        ctx.close();
    }
}
