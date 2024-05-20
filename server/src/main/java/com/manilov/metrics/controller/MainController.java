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
public class MainController {

    @Autowired
    private MetricService metricService;

    @PostMapping("/")
    @ResponseStatus(HttpStatus.OK)
    public void handleTime(@RequestParam("time") long clientTime) {
        metricService.updateDelay(clientTime);
    }
}
