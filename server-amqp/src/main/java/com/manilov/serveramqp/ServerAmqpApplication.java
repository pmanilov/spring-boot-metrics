package com.manilov.serveramqp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.manilov.service")
public class ServerAmqpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerAmqpApplication.class, args);
    }

}
