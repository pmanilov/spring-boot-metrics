package com.manilov.common.service;

import com.manilov.common.domain.Delay;
import com.manilov.common.repository.DelayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

@Service
@RequiredArgsConstructor
public class DelayService {
    private final DelayRepository delayRepository;
    private final AtomicLong receivedCount = new AtomicLong(0);
    private final DoubleAdder delaySumMs = new DoubleAdder();
    private final LongAdder delayCount = new LongAdder();

    public Double getAverageDelay(String serverId) {
        long count = delayCount.sum();
        if (count == 0) {
            return 0.0;
        }
        return delaySumMs.sum() / count;
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
        double delay = (double) (nanoTime - clientTime) / 1_000_000;
        delaySumMs.add(delay);
        delayCount.increment();
        delayRepository.save(new Delay(now, serverId, delay));
    }

    public void deleteAll(String serverId) {
        delaySumMs.reset();
        delayCount.reset();
        delayRepository.deleteAll(serverId);
    }
}
