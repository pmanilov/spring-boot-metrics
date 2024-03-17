package com.manilov.servercoap.configuration;

import com.manilov.servercoap.handler.CoapHandler;
import lombok.RequiredArgsConstructor;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class CoapConfiguration {

    private final CoapHandler coapHandler;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            CoapConfig.register();
            CoapServer coapServer = new CoapServer(5683);
            coapServer.add(coapHandler);
            coapServer.start();
        };
    }
}
