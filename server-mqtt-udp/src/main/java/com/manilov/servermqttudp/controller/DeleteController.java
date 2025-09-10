package com.manilov.servermqttudp.controller;

import com.manilov.common.service.DelayService;
import com.manilov.common.service.PacketSizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeleteController {
    private final DelayService delayService;
    private final PacketSizeService packetSizeService;

    @PostMapping("/metrics/delete")
    public ResponseEntity<String> delete() {
        delayService.deleteAll("mqtt-udp");
        packetSizeService.deleteAll("mqtt-udp");
        return ResponseEntity.ok("Metrics deleted successfully");
    }
}
