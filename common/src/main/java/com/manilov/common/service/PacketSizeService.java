package com.manilov.common.service;

import com.manilov.common.domain.PacketSize;
import com.manilov.common.repository.PacketSizeRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.LongAdder;

@Service
public class PacketSizeService {
    private final PacketSizeRepository packetSizeRepository;
    private final LongAdder packetSizeSum = new LongAdder();
    private final LongAdder packetSizeCount = new LongAdder();

    public PacketSizeService(ObjectProvider<PacketSizeRepository> packetSizeRepositoryProvider) {
        this.packetSizeRepository = packetSizeRepositoryProvider.getIfAvailable();
    }

    public Double getAveragePacketSize(String serverId) {
        long count = packetSizeCount.sum();
        if (count == 0) {
            return 0.0;
        }
        return (double) packetSizeSum.sum() / count;
    }

    public void save(PacketSize packetSize) {
        packetSizeSum.add(packetSize.getPacketSize());
        packetSizeCount.increment();
        if (packetSizeRepository != null) {
            packetSizeRepository.save(packetSize);
        }
    }

    public void deleteAll(String serverId) {
        packetSizeSum.reset();
        packetSizeCount.reset();
        if (packetSizeRepository != null) {
            packetSizeRepository.deleteAll(serverId);
        }
    }
}
