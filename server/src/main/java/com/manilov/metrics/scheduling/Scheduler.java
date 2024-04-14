package com.manilov.metrics.scheduling;

import com.manilov.service.MetricService;
import com.manilov.util.MutableDouble;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class Scheduler {

    private final MetricService metricService;

    private final MutableDouble avgThroughput;
    private final MutableDouble avgDelay;
    private final MutableDouble avgSize;

    public Scheduler(MeterRegistry meterRegistry, MetricService metricService) {
        avgThroughput = meterRegistry.gauge("avg_throughput", new MutableDouble(0));
        avgDelay = meterRegistry.gauge("avg_delay", new MutableDouble(0));
        avgSize = meterRegistry.gauge("avg_packet_size", new MutableDouble(0));
        this.metricService = metricService;
    }

    @Scheduled(fixedDelay = 500, initialDelay = 500)
    public void schedulingTask() {
        avgThroughput.setValue(metricService.getAvgThroughput());
        avgDelay.setValue(metricService.getAvgDelay());
        avgSize.setValue(metricService.getAvgSize());
    }
}
