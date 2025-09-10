package com.manilov.common.service;

import com.manilov.common.domain.Delay;
import com.manilov.common.repository.DelayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DelayService {
    private final DelayRepository delayRepository;

    public void save(long clientTime, String serverId) {
        Instant now = Instant.now();
        long nanoTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
        double delay = (double) (nanoTime - clientTime) / 1000000;
        delayRepository.save(new Delay(now, serverId, delay));
    }

    public void deleteAll() {
        delayRepository.deleteAll();
    }
}
