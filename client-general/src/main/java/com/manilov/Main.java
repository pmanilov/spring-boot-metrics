package com.manilov;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Main {

    private final static String HOSTNAME = "localhost";
    private final static Long PERIOD = 500L;
    private final static Integer COUNT_CLIENTS = 1;
    private final static String BROKER_MQTT = "tcp://" + HOSTNAME + ":1884";
    private final static String TOPIC_MQTT = "metricsTopic";
    private final static String CLIENT_ID_PREFIX_MQTT = "JavaMqttPublisher";
    private final static String TOPIC_MQTT_UDP = "metricsTopic";

    public static void main(String[] args) {

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            final String clientId = CLIENT_ID_PREFIX_MQTT + i;
            Runnable taskMQTT = getTaskMQTT(clientId);
            Thread.startVirtualThread(taskMQTT);
        }

        for (int i = 0; i < COUNT_CLIENTS; i++) {
            Thread.startVirtualThread(getTaskMqttUdp());
        }

        while (!Thread.interrupted()){

        }
    }

    private static Runnable getTaskMqttUdp() {
        return () -> {
            while (!Thread.interrupted()) {
                Instant now = Instant.now();
                long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                String payload = String.valueOf(currentTime);
                try {
                    PublishPacket pkt = new PublishPacket(TOPIC_MQTT_UDP, payload);
                    pkt.send();
                    System.out.println("MQTT-UDP sent: " + payload);
                    Long random = ThreadLocalRandom.current().nextLong(PERIOD) / 2;
                    TimeUnit.MILLISECONDS.sleep(PERIOD + random);
                } catch (InterruptedException | IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        };
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
}