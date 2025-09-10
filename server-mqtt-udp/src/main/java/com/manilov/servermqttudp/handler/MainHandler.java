package com.manilov.servermqttudp.handler;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.service.PacketSizeService;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class MainHandler {

    @Autowired
    private PacketSizeService packetSizeService;

    private PcapHandle handle;

    public MainHandler() {
        try {
            PcapNetworkInterface networkInterface = Pcaps.getDevByName("any");
            int snapshotLength = 65536;
            int readTimeout = 10;

            handle = networkInterface.openLive(snapshotLength, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, readTimeout);
            log.info("Started pcap4j capture on interface: {}", networkInterface.getName());

            Runnable captureTask = this::startCapture;
            Thread captureThread = new Thread(captureTask, "UDP-Capture-Thread");
            captureThread.setDaemon(true);
            captureThread.start();
        } catch (PcapNativeException e) {
            log.error("Pcap init failed: {}", e.getMessage(), e);
        }
    }

    public void startCapture() {
        while (true) {
            try {
                Packet packet = handle.getNextPacketEx();

                if (packet.contains(UdpPacket.class)) {
                    UdpPacket udpPacket = packet.get(UdpPacket.class);
                    int dstPort = udpPacket.getHeader().getDstPort().valueAsInt();

                    if (dstPort == 1883) {
                        byte[] payload = udpPacket.getPayload() != null
                                ? udpPacket.getPayload().getRawData()
                                : null;

                        if (payload != null && isMqttPublishMessage(payload)) {
                            int totalPacketSize =  packet.getRawData().length;
                            packetSizeService.save(new PacketSize(Instant.now(), "mqtt-udp", totalPacketSize));
                            //log.info("Captured MQTT/UDP PUBLISH packet, size: {} bytes", totalPacketSize);
                        }
                    }
                }

            } catch (PcapNativeException | NotOpenException | EOFException | TimeoutException e) {
                log.error("Packet capture error: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isMqttPublishMessage(byte[] payload) {
        return payload.length > 1 && (payload[0] & 0xF0) == 0x30;
    }
}
