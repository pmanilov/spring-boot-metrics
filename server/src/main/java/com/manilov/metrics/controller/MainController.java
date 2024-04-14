package com.manilov.metrics.controller;


import com.manilov.service.MetricService;
import lombok.extern.slf4j.Slf4j;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.EOFException;
import java.util.concurrent.TimeoutException;

@Controller
@Slf4j
public class MainController {

    @Autowired
    private MetricService metricService;

    private PcapHandle handle;

    public MainController() {
        try {
            PcapNetworkInterface networkInterface = Pcaps.getDevByName("any");
            int snapshotLength = 65536;
            handle = networkInterface.openLive(snapshotLength, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 10);
            Runnable captureTask = this::startCapture;
            Thread captureThread = new Thread(captureTask);
            captureThread.start();
        } catch (PcapNativeException e) {
            log.error(e.getMessage());
        }
    }

    public void startCapture() {
        while (true) {
            try {
                Packet packet = handle.getNextPacketEx();
                if (packet.contains(TcpPacket.class)) {
                    TcpPacket tcpPacket = packet.get(TcpPacket.class);
                    if (tcpPacket.getHeader().getDstPort().valueAsInt() == 8080) {
                        if (tcpPacket.getPayload() != null) {
                            byte[] httpPayload = tcpPacket.getPayload().getRawData();
                            //String httpContent = new String(httpPayload);
                            int totalPacketSize = httpPayload.length;
                            metricService.updateSize(totalPacketSize);
                        }
                    }
                }
            } catch (PcapNativeException | NotOpenException | EOFException | TimeoutException e) {
                log.error(e.getMessage());
            }
        }
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.OK)
    public void handleTime(@RequestParam("time") long clientTime) {
        metricService.updateDelay(clientTime);
    }
}
