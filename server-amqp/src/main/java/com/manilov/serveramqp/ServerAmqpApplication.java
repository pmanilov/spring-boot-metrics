package com.manilov.serveramqp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServerAmqpApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerAmqpApplication.class, args);
    }

}
