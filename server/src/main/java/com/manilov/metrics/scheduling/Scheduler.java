package com.manilov.metrics.scheduling;

import com.manilov.metrics.service.MetricService;
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
    private MetricService metricService;

    private final AtomicLong testTime;
    private final MutableDouble avgDelay;
    private final MutableDouble avgSize;

    public Scheduler(MeterRegistry meterRegistry) {
        testTime = meterRegistry.gauge("time_millis", new AtomicLong(System.currentTimeMillis()));
        avgDelay = meterRegistry.gauge("avg_delay", new MutableDouble(0));
        avgSize = meterRegistry.gauge("avg_packet_size", new MutableDouble(0));
    }

    @Scheduled(fixedDelay = 500, initialDelay = 500)
    public void schedulingTask() {
        testTime.set(System.currentTimeMillis());
        avgDelay.setValue(metricService.getAvgDelay());
        avgSize.setValue(metricService.getAvgSize());
    }
}
