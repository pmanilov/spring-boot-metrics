package com.manilov.servermqttquic.controller;

import com.manilov.common.service.DelayService;
import com.manilov.common.service.PacketSizeService;
import com.manilov.servermqttquic.configuration.MqttQuicSubscriberConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    private final MqttQuicSubscriberConfiguration subscriber;

    @Value("${server.id}")
    private String serverId;

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        if (subscriber.isSubscribed()) {
            return ResponseEntity.ok("ready");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("not ready");
    }

    @PostMapping("/recycle")
    public ResponseEntity<String> recycle() {
        subscriber.recycle();
        return ResponseEntity.accepted().body("recycling");
    }

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

    @GetMapping("/delay/median")
    public ResponseEntity<Double> getDelayMedian() {
        return ResponseEntity.ok(delayService.getDelayPercentile(serverId, 0.50));
    }

    @GetMapping("/delay/p95")
    public ResponseEntity<Double> getDelayP95() {
        return ResponseEntity.ok(delayService.getDelayPercentile(serverId, 0.95));
    }

    @GetMapping("/delay/p99")
    public ResponseEntity<Double> getDelayP99() {
        return ResponseEntity.ok(delayService.getDelayPercentile(serverId, 0.99));
    }

    @GetMapping("/packet-size/avg")
    public ResponseEntity<Double> getAveragePacketSize() {
        return ResponseEntity.ok(packetSizeService.getAveragePacketSize(serverId));
    }

    @GetMapping("/count")
    public ResponseEntity<Long> getCount() {
        return ResponseEntity.ok(delayService.getReceivedCount());
    }

    @PostMapping("/count/reset")
    public ResponseEntity<String> resetCount() {
        delayService.resetReceivedCount();
        return ResponseEntity.ok("Count reset");
    }
}
