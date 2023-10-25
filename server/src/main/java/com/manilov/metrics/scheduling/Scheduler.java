package com.manilov.metrics.scheduling;

import com.manilov.metrics.service.TimeService;
import com.manilov.metrics.util.MutableDouble;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;


@Component
@Slf4j
public class Scheduler {

    @Autowired
    private TimeService timeService;

    private final AtomicLong testTime;
    private MutableDouble avgDelay;

    public Scheduler(MeterRegistry meterRegistry) {
        testTime = meterRegistry.gauge("time_millis", new AtomicLong(System.currentTimeMillis()));
        avgDelay = meterRegistry.gauge("avg_delay", new MutableDouble(0));
    }

    @Scheduled(fixedDelay = 500, initialDelay = 0)
    public void schedulingTask() {
        testTime.set(System.currentTimeMillis());
        avgDelay.setValue(timeService.getAvgDelay());
    }
}
