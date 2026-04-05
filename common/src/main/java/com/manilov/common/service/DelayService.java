package com.manilov.common.service;

import com.manilov.common.domain.Delay;
import com.manilov.common.repository.DelayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class DelayService {
    private final DelayRepository delayRepository;
    private final AtomicLong receivedCount = new AtomicLong(0);

    public Double getAverageDelay(String serverId) {
        return delayRepository.selectAverageDelay(serverId);
    }

    public long getReceivedCount() {
        return receivedCount.get();
    }

    public void resetReceivedCount() {
        receivedCount.set(0);
    }

    public void save(long clientTime, String serverId) {
        receivedCount.incrementAndGet();
        Instant now = Instant.now();
        long nanoTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
        double delay = (double) (nanoTime - clientTime) / 1000000;
        delayRepository.save(new Delay(now, serverId, delay));
    }

    public void deleteAll(String serverId) {
        delayRepository.deleteAll(serverId);
    }
}
