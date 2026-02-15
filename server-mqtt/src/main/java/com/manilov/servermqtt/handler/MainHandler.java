package com.manilov.servermqtt.handler;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.service.PacketSizeService;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class MainHandler {
    private final PacketSizeService packetSizeService;
    private PcapHandle handle;

    @Value("${server.id}")
    private String serverId;

    public MainHandler(PacketSizeService  packetSizeService) {
        this.packetSizeService = packetSizeService;
        try {
            PcapNetworkInterface networkInterface = Pcaps.getDevByName("any");
            int snapshotLength = 65536;
            handle = networkInterface.openLive(snapshotLength, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
            Runnable captureTask = this::startCapture;
            Thread captureThread = new Thread(captureTask);
            captureThread.start();
        } catch (PcapNativeException e) {
            System.err.println(e.getMessage());
        }
    }

    public void startCapture() {
        while (true) {
            try {
                Packet packet = handle.getNextPacketEx();
                if (packet.contains(TcpPacket.class)) {
                    TcpPacket tcpPacket = packet.get(TcpPacket.class);
                    if (tcpPacket.getHeader().getSrcPort().valueAsInt() == 1884) {
                        if (tcpPacket.getPayload() != null) {
                            byte[] mqttPayload = tcpPacket.getPayload().getRawData();
                            if (isMqttPublishMessage(mqttPayload)) {
                                int totalPacketSize = packet.getRawData().length;
                                packetSizeService.save(new PacketSize(Instant.now(), serverId, totalPacketSize));
                            }
                        }
                    }
                }
            } catch (PcapNativeException | NotOpenException | EOFException | TimeoutException e) {
                if (e.getMessage() != null) {
                    log.error(e.getMessage());
                }
            }
        }
    }

    private boolean isMqttPublishMessage(byte[] mqttPayload) {
        return mqttPayload.length > 1 && mqttPayload[0] == (byte) 0x30;
    }
}
