package com.manilov.servermqtt.controller;

import com.manilov.common.service.DelayService;
import com.manilov.common.service.PacketSizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/metrics")
public class MetricsController {
    private final DelayService delayService;
    private final PacketSizeService packetSizeService;

    @Value("${server.id}")
    private String serverId;

    @PostMapping("/delete")
    public ResponseEntity<String> delete() {
        delayService.deleteAll(serverId);
        packetSizeService.deleteAll(serverId);
        return ResponseEntity.ok("Metrics deleted successfully");
    }

    @GetMapping("/delay/avg")
    public ResponseEntity<Double> getAverageDelay() {
        return ResponseEntity.ok(delayService.getAverageDelay(serverId));
    }

    @GetMapping("/packet-size/avg")
    public ResponseEntity<Double> getAveragePacketSize() {
        return ResponseEntity.ok(packetSizeService.getAveragePacketSize(serverId));
    }
}
