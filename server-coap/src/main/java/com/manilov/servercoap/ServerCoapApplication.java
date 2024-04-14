package com.manilov.servercoap;

import com.manilov.servercoap.handler.CoapHandler;
import org.eclipse.californium.core.CoapServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = "com.manilov.service")
public class ServerCoapApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerCoapApplication.class, args);
    }
}
