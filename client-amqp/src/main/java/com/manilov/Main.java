package com.manilov;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    private final static String QUEUE_NAME = "metricsQueue";

    public static void main(String[] argv) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try (Connection connection = factory.newConnection();
                     Channel channel = connection.createChannel()) {
                    channel.queueDeclare(QUEUE_NAME, false, false, false, null);
                    while (true) {
                        String message = String.valueOf(System.currentTimeMillis());
                        channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
                        System.out.println(" [x] Sent '" + message + "'");
                        TimeUnit.SECONDS.sleep(5);
                    }
                } catch (TimeoutException | IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                }
            }).start();
        }
    }
}