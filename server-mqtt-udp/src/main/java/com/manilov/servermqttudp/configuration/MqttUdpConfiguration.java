package com.manilov.servermqttudp.configuration;

import com.manilov.common.service.DelayService;
import com.manilov.servermqttudp.handler.PacketSizeHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PacketSourceServer;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttUdpConfiguration {
    private final DelayService delayService;
    private final PacketSizeHandler packetSizeHandler;
    private final PacketSourceServer receiver = new PacketSourceServer();

    @Value("${server.id}")
    private String serverId;
    @Value("${metrics.topic}")
    private String metricsTopic;

    @PostConstruct
    public void init() {
        Engine.setThrottle(0);

        receiver.setSink(pkt -> {
            if (pkt instanceof PublishPacket pub) {
                handlePacket(pub);
            }
        });
    }

    private void handlePacket(PublishPacket pub) {
        String topic = pub.getTopic();
        if (metricsTopic.equals(topic)) {
            try {
                String valueStr = pub.getValueString();
                long sentTs = Long.parseLong(valueStr.split(",")[0]);
                delayService.save(sentTs, serverId);

                byte[] payloadBytes = valueStr.getBytes(StandardCharsets.UTF_8);
                packetSizeHandler.handleMessage(topic, payloadBytes);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    @Bean
    public UdpMqttSender udpMqttSender() {
        return new UdpMqttSender();
    }

    public static class UdpMqttSender {
        public void send(String topic, String value) throws IOException {
            PublishPacket pkt = new PublishPacket(topic, value);
            pkt.send();
        }
    }
}