package com.manilov;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        if (Config.enableMqtt) {
            for (int i = 0; i < Config.countClients; i++) {
                final String clientId = Config.clientIdPrefixMqtt + i;
                Runnable taskMQTT = getTaskMQTT(clientId);
                Thread.startVirtualThread(taskMQTT);
            }
        }

        if (Config.enableMqttUdp) {
            Engine.setThrottle(0);
            for (int i = 0; i < Config.countClients; i++) {
                Thread.startVirtualThread(getTaskMqttUdp());
            }
        }
    }

    private static long getNextPoissonDelayNanos(double lambda) {
        if (lambda <= 0) return Long.MAX_VALUE;

        double random = ThreadLocalRandom.current().nextDouble();
        double intervalSeconds = -Math.log(1.0 - random) / lambda;

        return (long) (intervalSeconds * 1_000_000_000L);
    }

    private static Runnable getTaskMqttUdp() {
        return () -> {
            while (!Thread.interrupted() && Config.isRunning) {

                Instant now = Instant.now();
                long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                String payload = String.valueOf(currentTime);
                try {
                    PublishPacket pkt = new PublishPacket(Config.topicMqttUdp, payload);
                    pkt.send();
                    System.out.println("MQTT-UDP sent: " + payload);
                    long sleepNanos = getNextPoissonDelayNanos(Config.intensity);
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                } catch (InterruptedException | IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        };
    }

    private static Runnable getTaskMQTT(String clientId) {
        return () -> {
            try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles/" + clientId);
                 MqttClient client = new MqttClient(Config.getBrokerUrl(), clientId, persistence)) {
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);
                connOpts.setConnectionTimeout(10);

                System.out.println("Connecting to broker: " + Config.getBrokerUrl());
                client.connect(connOpts);
                System.out.println("Connected: " + clientId);

                while (client.isConnected() && Config.isRunning) {

                    Instant now = Instant.now();
                    long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                    String message = String.valueOf(currentTime);
                    MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                    mqttMessage.setQos(0);
                    client.publish(Config.topicMqtt, mqttMessage);
                    System.out.println(clientId + " published: " + message);

                    long sleepNanos = getNextPoissonDelayNanos(Config.intensity);
                    TimeUnit.NANOSECONDS.sleep(sleepNanos);
                }
                client.disconnect();
            } catch (InterruptedException | MqttException e) {
                System.err.println(e.getMessage());
            }
        };
    }
}