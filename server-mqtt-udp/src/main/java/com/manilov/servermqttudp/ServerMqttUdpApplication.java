package com.manilov.servermqttudp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableScheduling
@ComponentScan(basePackages = {"com.manilov.common", "com.manilov.servermqttudp"})
public class ServerMqttUdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerMqttUdpApplication.class, args);
    }

}
