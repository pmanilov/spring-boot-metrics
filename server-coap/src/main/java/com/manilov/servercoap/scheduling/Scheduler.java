package com.manilov.servercoap.scheduling;

import com.manilov.servercoap.service.MetricService;
import com.manilov.servercoap.util.MutableDouble;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class Scheduler {

    @Autowired
    private MetricService metricService;

    private final MutableDouble avgThroughput;
    private final MutableDouble avgDelay;
    private final MutableDouble avgSize;

    public Scheduler(MeterRegistry meterRegistry) {
        avgThroughput = meterRegistry.gauge("avg_throughput_coap", new MutableDouble(0));
        avgDelay = meterRegistry.gauge("avg_delay_coap", new MutableDouble(0));
        avgSize = meterRegistry.gauge("avg_packet_size_coap", new MutableDouble(0));
    }

    @Scheduled(fixedDelay = 500, initialDelay = 500)
    public void schedulingTask() {
        avgThroughput.setValue(metricService.getAvgThroughput());
        avgDelay.setValue(metricService.getAvgDelay());
        avgSize.setValue(metricService.getAvgSize());
    }
}
