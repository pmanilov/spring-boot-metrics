package com.manilov.serveramqp.configuration;

import com.manilov.service.MetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RabbitMQConfiguration {

    private final MetricService metricService;

    @Bean
    public Queue myQueue() {
        return new Queue("metricsQueue", false);
    }

    @RabbitListener(queues = "metricsQueue", ackMode = "NONE"/*, concurrency = "2-2"*/)
    public void listen(String in) {
        metricService.updateDelay(Long.parseLong(in));
        //log.info("Message read from metricsQueue : " + in);
    }
}
