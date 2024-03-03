package com.manilov.servermqtt.handler;

import com.manilov.servermqtt.service.MetricService;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.io.EOFException;
import java.util.concurrent.TimeoutException;

//@Component
public class MainHandler {

    //@Autowired
    private MetricService metricService;

    private PcapHandle handle;

    public MainHandler() {
        try {
            PcapNetworkInterface networkInterface = Pcaps.getDevByName("lo");
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
                    if (tcpPacket.getHeader().getDstPort().valueAsInt() == 8081) {
                        if (tcpPacket.getPayload() != null) {
                            byte[] mqttPayload = tcpPacket.getRawData();
                            String mqttContent = new String(mqttPayload);
                            int totalPacketSize = mqttPayload.length;
                            metricService.updateSize(totalPacketSize);
                        }
                    }
                }
            } catch (PcapNativeException | NotOpenException | EOFException | TimeoutException e) {
                System.err.println(e.getMessage());
            }
        }
    }
}
