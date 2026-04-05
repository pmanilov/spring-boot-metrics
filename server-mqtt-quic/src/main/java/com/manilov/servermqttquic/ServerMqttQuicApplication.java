package com.manilov.servermqttquic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.manilov.common", "com.manilov.servermqttquic"})
public class ServerMqttQuicApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerMqttQuicApplication.class, args);
    }
}
