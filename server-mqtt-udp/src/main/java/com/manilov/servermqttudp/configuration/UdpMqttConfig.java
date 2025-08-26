package com.manilov.servermqttudp.configuration;

import com.manilov.service.MetricService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.dz.mqtt_udp.PacketSourceServer;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.util.concurrent.*;

@Configuration
@RequiredArgsConstructor
public class UdpMqttConfig {

    private final MetricService metricService;
    private PacketSourceServer receiver;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        receiver = new PacketSourceServer();

        receiver.setSink(pkt -> {
            if (pkt instanceof PublishPacket pub) {
                executor.submit(() -> handlePacket(pub));
            }
        });
    }

    private void handlePacket(PublishPacket pub) {
        String topic = pub.getTopic();
        if ("metricsTopic".equals(topic)) {
            String value = pub.getValueString();
            try {
                long sentTs = Long.parseLong(value);
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