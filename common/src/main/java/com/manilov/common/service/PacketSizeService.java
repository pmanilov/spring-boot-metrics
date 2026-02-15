package com.manilov.common.service;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.repository.PacketSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PacketSizeService {
    private final PacketSizeRepository packetSizeRepository;

    public Double getAveragePacketSize(String serverId) {
        return packetSizeRepository.selectAveragePacketSize(serverId);
    }

    public void save(PacketSize packetSize) {
        packetSizeRepository.save(packetSize);
    }

    public void deleteAll(String serverId) {
        packetSizeRepository.deleteAll(serverId);
    }
}
