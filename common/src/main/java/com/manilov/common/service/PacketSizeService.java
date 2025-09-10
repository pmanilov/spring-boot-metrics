package com.manilov.common.service;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.repository.PacketSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PacketSizeService {
    private final PacketSizeRepository packetSizeRepository;

    public void save(PacketSize packetSize) {
        packetSizeRepository.save(packetSize);
    }

    public void deleteAll() {
        packetSizeRepository.deleteAll();
    }
}
