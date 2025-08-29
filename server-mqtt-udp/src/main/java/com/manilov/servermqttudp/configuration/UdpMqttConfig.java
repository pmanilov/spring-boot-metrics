package com.manilov.servermqttudp.configuration;

import com.manilov.service.MetricService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PacketSourceServer;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.util.concurrent.*;

@Configuration
@RequiredArgsConstructor
public class UdpMqttConfig {
    private static final String METRICS_TOPIC = "metricsTopic";

    private final MetricService metricService;
    private final PacketSourceServer receiver = new PacketSourceServer();;

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
        if (METRICS_TOPIC.equals(topic)) {
            try {
                long sentTs = Long.parseLong(pub.getValueString());
                metricService.updateDelay(sentTs);
            } catch (NumberFormatException ignored) {}
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