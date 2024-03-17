package com.manilov.servercoap.handler;

import com.manilov.servercoap.service.MetricService;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class MainHandler {

    @Autowired
    private MetricService metricService;

    private PcapHandle handle;

    public MainHandler() {
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
                if (packet.contains(UdpPacket.class)) {
                    UdpPacket udpPacket = packet.get(UdpPacket.class);
                    if (udpPacket.getHeader().getDstPort().valueAsInt() == 5683) {
                        if (udpPacket.getPayload() != null) {
                            byte[] coapPayload = udpPacket.getPayload().getRawData();
                            //String coapContent = new String(coapPayload);
                            int totalPacketSize = coapPayload.length;
                            metricService.updateSize(totalPacketSize);
                        }
                    }
                }
            } catch (PcapNativeException | NotOpenException | EOFException | TimeoutException e) {
                log.error(e.getMessage());
            }
        }
    }
}
