package com.manilov;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.exception.ConnectorException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String coapUrl = "coap://localhost:5683/metrics";
        for (int i = 0; i < 3; i++) {
            Runnable task = () -> {
                CoapConfig.register();
                CoapClient coapClient = new CoapClient(coapUrl);
                try {
                    while (!Thread.interrupted()) {
                        String payload = String.valueOf(System.currentTimeMillis());
                        CoapResponse response = coapClient.post(payload, 0);
                        System.out.println("POST Response: " + response.getResponseText());
                        TimeUnit.SECONDS.sleep(5);
                    }
                } catch (ConnectorException | IOException | InterruptedException e) {
                    System.err.println(e.getMessage());
                }
                coapClient.shutdown();
            };
            new Thread(task).start();
        }
    }
}