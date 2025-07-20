package com.manilov.servermqttudp.configuration;

import com.manilov.service.MetricService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import ru.dz.mqtt_udp.PacketSourceServer;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class UdpMqttConfig {

    private final MetricService metricService;
    private PacketSourceServer receiver;

    @PostConstruct
    public void init() {
        receiver = new PacketSourceServer();
        receiver.setSink(pkt -> {
            if (pkt instanceof PublishPacket) {
                PublishPacket pub = (PublishPacket) pkt;
                String topic = pub.getTopic();
                if ("metricsTopic".equals(topic)) {
                    String value = pub.getValueString();
                    try {
                        long sentTs = Long.parseLong(value);
                        metricService.updateDelay(sentTs);
                    } catch (NumberFormatException ignored) {}
                }
            }
        });
    }


    public void send(String topic, String value) throws IOException {
        PublishPacket pkt = new PublishPacket(topic, value);
        pkt.send();
    }
}
