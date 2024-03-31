package com.manilov;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import okhttp3.*;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    private final static Long PERIOD = 2L;
    private final static Integer COUNT_CLIENTS = 3;
    private final static String BROKER_MQTT = "tcp://localhost:1883";
    private final static String TOPIC_MQTT = "metricsTopic";
    private final static String CLIENT_ID_PREFIX_MQTT = "JavaMqttPublisher";
    private final static String COAP_URL = "coap://localhost:5683/metrics";
    private final static String QUEUE_NAME_AMQP = "metricsQueue";

    public static void main(String[] args) {
        OkHttpClient okHttpClient = new OkHttpClient();
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(8);

        Runnable taskHTTP = () -> {
            RequestBody formBody = new FormBody.Builder()
                    .add("time", String.valueOf(System.currentTimeMillis()))
                    .build();
            Request request = new Request.Builder()
                    .url("http://localhost:8080/")
                    .post(formBody)
                    .build();
            try {
                Response response = okHttpClient.newCall(request).execute();
                //String stringResponse = response.body().string();
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        };

        long initialDelay = 0;

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            executorService.scheduleAtFixedRate(taskHTTP, initialDelay, PERIOD, TimeUnit.SECONDS);
        }

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            final String clientId = CLIENT_ID_PREFIX_MQTT + i;
            Runnable taskMQTT = () -> {
                try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles");
                     MqttClient client = new MqttClient(BROKER_MQTT, clientId, persistence)) {
                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(true);

                    System.out.println("Connecting to broker: " + BROKER_MQTT);
                    client.connect(connOpts);
                    System.out.println("Connected");

                    while (client.isConnected()) {
                        String message = String.valueOf(System.currentTimeMillis());
                        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                        mqttMessage.setQos(0);
                        //System.out.println("Publishing message: " + message);
                        client.publish(TOPIC_MQTT, mqttMessage);
                        System.out.println("Message published: " + message);
                        TimeUnit.SECONDS.sleep(PERIOD);
                    }
                } catch (InterruptedException | MqttException e) {
                    System.err.println(e.getMessage());
                }
            };
            new Thread(taskMQTT).start();
        }

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            Runnable taskCoAP = () -> {
                CoapConfig.register();
                CoapClient coapClient = new CoapClient(COAP_URL);
                try {
                    while (!Thread.interrupted()) {
                        String payload = String.valueOf(System.currentTimeMillis());
                        CoapResponse response = coapClient.post(payload, 0);
                        System.out.println("POST Response: " + response.getResponseText());
                        TimeUnit.SECONDS.sleep(PERIOD);
                    }
                } catch (ConnectorException | IOException | InterruptedException e) {
                    System.err.println(e.getMessage());
                }
                coapClient.shutdown();
            };
            new Thread(taskCoAP).start();
        }
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        for (int i = 0; i < COUNT_CLIENTS; i++) {
            new Thread(() -> {
                try (Connection connection = factory.newConnection();
                     Channel channel = connection.createChannel()) {
                    channel.queueDeclare(QUEUE_NAME_AMQP, false, false, false, null);
                    while (true) {
                        String message = String.valueOf(System.currentTimeMillis());
                        channel.basicPublish("", QUEUE_NAME_AMQP, null, message.getBytes(StandardCharsets.UTF_8));
                        System.out.println(" [x] Sent '" + message + "'");
                        TimeUnit.SECONDS.sleep(PERIOD);
                    }
                } catch (TimeoutException | IOException | InterruptedException e) {
                    System.out.println(e.getMessage());
                }
            }).start();
        }
    }
}