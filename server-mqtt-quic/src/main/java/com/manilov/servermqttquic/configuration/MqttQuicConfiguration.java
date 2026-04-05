package com.manilov.servermqttquic.configuration;

import com.manilov.common.service.DelayService;
import com.manilov.servermqttquic.handler.MqttQuicBrokerHandler;
import com.manilov.servermqttquic.handler.PacketSizeHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicServerCodecBuilder;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Starts a Netty QUIC server that carries MQTT control packets. Each QUIC
 * bidirectional stream is a full MQTT session (CONNECT/PUBLISH/...) encoded
 * with Netty's stock MQTT codec — no custom framing.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttQuicConfiguration {

    private final DelayService delayService;
    private final PacketSizeHandler packetSizeHandler;

    @Value("${server.id}")
    private String serverId;
    @Value("${metrics.topic}")
    private String metricsTopic;
    @Value("${quic.port}")
    private int quicPort;
    @Value("${quic.alpn}")
    private String alpn;

    private NioEventLoopGroup group;
    private Channel udpChannel;

    @PostConstruct
    public void start() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder
                .forServer(ssc.key(), null, ssc.cert())
                .applicationProtocols(alpn)
                .build();

        group = new NioEventLoopGroup(1);

        ChannelHandler codec = new QuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(60, TimeUnit.SECONDS)
                .initialMaxData(100_000_000)
                .initialMaxStreamDataBidirectionalLocal(10_000_000)
                .initialMaxStreamDataBidirectionalRemote(10_000_000)
                .initialMaxStreamDataUnidirectional(10_000_000)
                .initialMaxStreamsBidirectional(10_000)
                .initialMaxStreamsUnidirectional(10_000)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        log.info("QUIC connection established from {}", ch.remoteAddress());
                    }
                })
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        ch.pipeline().addLast(MqttEncoder.INSTANCE);
                        ch.pipeline().addLast(new MqttDecoder());
                        ch.pipeline().addLast(new MqttQuicBrokerHandler(
                                delayService, packetSizeHandler, serverId, metricsTopic));
                    }
                })
                .build();

        udpChannel = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(quicPort)
                .sync()
                .channel();

        log.info("MQTT-over-QUIC server listening on UDP :{} (ALPN '{}')", quicPort, alpn);
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        if (udpChannel != null) {
            udpChannel.close().sync();
        }
        if (group != null) {
            group.shutdownGracefully().sync();
        }
    }
}
