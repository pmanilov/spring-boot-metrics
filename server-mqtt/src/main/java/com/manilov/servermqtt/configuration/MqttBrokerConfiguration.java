package com.manilov.servermqtt.configuration;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Properties;

@Configuration
@Slf4j
public class MqttBrokerConfiguration {

    private Server mqttBroker;

    @PostConstruct
    public void startBroker() {
        try {
            mqttBroker = new Server();
            MemoryConfig memoryConfig = new MemoryConfig(new Properties());
            mqttBroker.startServer(memoryConfig);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @PreDestroy
    public void stopBroker() {
        if (mqttBroker != null) {
            log.info("moquette broker stop");
        }
    }
}
