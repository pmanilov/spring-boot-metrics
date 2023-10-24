package com.manilov.metrics.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;


@Component
@Slf4j
public class Scheduler {

    private final AtomicLong testTime;

    public Scheduler(MeterRegistry meterRegistry) {
        testTime = meterRegistry.gauge("time_millis", new AtomicLong(System.currentTimeMillis()));
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 0)
    public void schedulingTask() {
        testTime.set(System.currentTimeMillis());
    }
}
