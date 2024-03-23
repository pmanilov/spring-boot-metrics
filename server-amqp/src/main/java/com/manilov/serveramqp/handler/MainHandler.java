package com.manilov.serveramqp.handler;

import com.manilov.serveramqp.service.MetricService;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
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
                if (packet.contains(TcpPacket.class)) {
                    TcpPacket tcpPacket = packet.get(TcpPacket.class);
                    if (tcpPacket.getHeader().getSrcPort().valueAsInt() == 5672) {
                        if (tcpPacket.getPayload() != null) {
                            byte[] amqpPayload = tcpPacket.getPayload().getRawData();
                            //String amqpContent = new String(amqpPayload);
                            int totalPacketSize = amqpPayload.length;
                            metricService.updateSize(totalPacketSize);
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
}
