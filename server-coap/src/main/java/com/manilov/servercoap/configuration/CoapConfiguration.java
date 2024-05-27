package com.manilov.servercoap.configuration;

import com.manilov.servercoap.handler.CoapHandler;
import lombok.RequiredArgsConstructor;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;


@Configuration
@RequiredArgsConstructor
public class CoapConfiguration {

    private final CoapHandler coapHandler;

    @Bean
    public CommandLineRunner commandLineRunner() {
        return args -> {
            CoapConfig.register();
            org.eclipse.californium.elements.config.Configuration configuration = org.eclipse.californium.elements.config.Configuration.getStandard();
            configuration.set(CoapConfig.EXCHANGE_LIFETIME, 5L, TimeUnit.SECONDS);
            CoapEndpoint coapEndpoint = CoapEndpoint.builder().setConfiguration(configuration).build();
            CoapServer coapServer = new CoapServer(5683);
            coapServer.add(coapHandler);
            coapServer.addEndpoint(coapEndpoint);
            coapServer.start();
        };
    }
}
