package com.manilov.servermqttquic.handler;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.service.PacketSizeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class PacketSizeHandler {
    private final PacketSizeService packetSizeService;

    @Value("${server.id}")
    private String serverId;

    public PacketSizeHandler(PacketSizeService packetSizeService) {
        this.packetSizeService = packetSizeService;
    }

    /**
     * Called for every MQTT PUBLISH frame received over QUIC. The packet size
     * recorded is the MQTT-layer packet size (fixed header + variable header +
     * payload), mirroring the metric used by the TCP and UDP MQTT servers so
     * the numbers are comparable across transports.
     */
    public void handleMessage(String topic, byte[] payload) {
        int topicBytes = topic.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int payloadLength = payload.length;
        int variableHeaderAndPayload = 2 + topicBytes + payloadLength;
        int remainingLengthBytes = computeRemainingLengthBytes(variableHeaderAndPayload);
        int totalSize = 1 + remainingLengthBytes + variableHeaderAndPayload;

        packetSizeService.save(new PacketSize(Instant.now(), serverId, totalSize));
    }

    private int computeRemainingLengthBytes(int remainingLength) {
        if (remainingLength <= 127)
            return 1;
        if (remainingLength <= 16383)
            return 2;
        if (remainingLength <= 2097151)
            return 3;
        return 4;
    }
}
