package com.manilov;

import com.rabbitmq.client.AMQP;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.*;

public class Main {

    private final static String HOSTNAME = "localhost";
    //private final static String HOSTNAME = "2.59.40.166";
    private final static Long PERIOD = 25L;
    private final static Integer COUNT_CLIENTS = 1;
    private final static String BROKER_MQTT = "tcp://" + HOSTNAME + ":1883";
    private final static String TOPIC_MQTT = "metricsTopic";
    private final static String CLIENT_ID_PREFIX_MQTT = "JavaMqttPublisher";
    private final static String COAP_URL = "coap://" + HOSTNAME + ":5683/metrics";
    private final static String QUEUE_NAME_AMQP = "metricsQueue";

    public static void main(String[] args) {

        Runnable taskHTTP = getTaskHTTP();
        for (int i = 0; i < COUNT_CLIENTS; i++) {
            Thread.startVirtualThread(taskHTTP);
        }

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            final String clientId = CLIENT_ID_PREFIX_MQTT + i;
            Runnable taskMQTT = getTaskMQTT(clientId);
            Thread.startVirtualThread(taskMQTT);
        }

        Runnable taskCoAP = getTaskCoAP();
        for (int i = 0; i < COUNT_CLIENTS; i++) {
            Thread.startVirtualThread(taskCoAP);
        }

        Runnable taskAMQP = getTaskAMQP();
        for (int i = 0; i < COUNT_CLIENTS; i++) {
            Thread.startVirtualThread(taskAMQP);
        }

        while (!Thread.interrupted()){

        }
    }

    private static Runnable getTaskMQTT(String clientId) {
        return () -> {
            try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles");
                 MqttClient client = new MqttClient(BROKER_MQTT, clientId, persistence)) {
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);

                System.out.println("Connecting to broker: " + BROKER_MQTT);
                client.connect(connOpts);
                System.out.println("Connected");

                while (client.isConnected()) {
                    Instant now = Instant.now();
                    long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                    String message = String.valueOf(currentTime);
                    MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                    mqttMessage.setQos(0);
                    //System.out.println("Publishing message: " + message);
                    client.publish(TOPIC_MQTT, mqttMessage);
                    System.out.println("Message published: " + message);
                    Long random = ThreadLocalRandom.current().nextLong(PERIOD) / 2;
                    TimeUnit.MILLISECONDS.sleep(PERIOD + random);
                    //TimeUnit.MILLISECONDS.sleep(PERIOD);
                }
            } catch (InterruptedException | MqttException e) {
                System.err.println(e.getMessage());
            }
        };
    }

    private static Runnable getTaskCoAP() {
        return () -> {
            CoapConfig.register();
            CoapClient coapClient = new CoapClient(COAP_URL);
                while (!Thread.interrupted()) {
                    try {
                        Instant now = Instant.now();
                        long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                        String payload = String.valueOf(currentTime);
                        CoapResponse response = coapClient.post(payload, 0);
                        System.out.println("POST Response: " + response.getResponseText());
                        Long random = ThreadLocalRandom.current().nextLong(PERIOD) / 2;
                        TimeUnit.MILLISECONDS.sleep(PERIOD + random);
                        //TimeUnit.MILLISECONDS.sleep(PERIOD);
                    }
                    catch (ConnectorException | IOException | InterruptedException e) {
                        System.err.println(e.getMessage());
                    }
            }
            coapClient.shutdown();
        };
    }

    private static Runnable getTaskHTTP() {
        OkHttpClient okHttpClient = new OkHttpClient();
        return () -> {
            while (!Thread.interrupted()) {
                Instant now = Instant.now();
                long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                RequestBody formBody = new FormBody.Builder()
                        .add("time", String.valueOf(currentTime))
                        .build();
                Request request = new Request.Builder()
                        .url("http://" + HOSTNAME + ":8080/")
                        .post(formBody)
                        .build();
                try {
                    Response response = okHttpClient.newCall(request).execute();
                    //String stringResponse = response.body().string();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
                try {
                    Long random = ThreadLocalRandom.current().nextLong(PERIOD) / 2;
                    TimeUnit.MILLISECONDS.sleep(PERIOD + random);
                    //TimeUnit.MILLISECONDS.sleep(PERIOD);
                } catch (InterruptedException e) {
                    System.err.println(e.getMessage());
                }
            }
        };
    }

    private static Runnable getTaskAMQP() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOSTNAME);
        return () -> {
            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                        .deliveryMode(1)
                        .build();
                channel.queueDeclare(QUEUE_NAME_AMQP, false, false, false, null);
                channel.basicQos(0);
                while (!Thread.interrupted()) {
                    Instant now = Instant.now();
                    long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                    String message = String.valueOf(currentTime);
                    channel.basicPublish("", QUEUE_NAME_AMQP, props, message.getBytes(StandardCharsets.UTF_8));
                    System.out.println(" [x] Sent '" + message + "'");
                    Long random = ThreadLocalRandom.current().nextLong(PERIOD) / 2;
                    TimeUnit.MILLISECONDS.sleep(PERIOD + random);
                    //TimeUnit.MILLISECONDS.sleep(PERIOD);
                }
            } catch (TimeoutException | IOException | InterruptedException e) {
                System.err.println(e.getMessage());
            }
        };
    }
}