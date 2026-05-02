package com.manilov.servermqtt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.manilov.common", "com.manilov.servermqtt"})
public class ServerMqttApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerMqttApplication.class, args);
    }
}
