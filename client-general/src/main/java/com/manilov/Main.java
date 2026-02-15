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
            while (Config.isRunning) {

                try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles/" + clientId);
                     MqttClient client = new MqttClient(Config.getBrokerUrl(), clientId, persistence)) {

                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(true);

                    connOpts.setKeepAliveInterval(5);

                    connOpts.setAutomaticReconnect(false);

                    System.out.println(clientId + ": Connecting to broker...");

                    try {
                        client.connect(connOpts);
                        System.out.println(clientId + ": Connected!");

                        while (client.isConnected() && Config.isRunning) {

                            Instant now = Instant.now();
                            long currentTime = now.getEpochSecond() * 1_000_000_000L + now.getNano();
                            String message = String.valueOf(currentTime);

                            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                            mqttMessage.setQos(0);

                            client.publish(Config.topicMqtt, mqttMessage);
                            System.out.println(clientId + " published: " + message);

                            long sleepNanos = getNextPoissonDelayNanos(Config.intensity);
                            TimeUnit.NANOSECONDS.sleep(sleepNanos);
                        }

                    } catch (MqttException e) {
                        System.err.println(clientId + " Connection lost/failed: " + e.getMessage() + " (" + e.getReasonCode() + ")");
                    }

                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        break;
                    }
                    System.err.println(clientId + " Critical error: " + e.getMessage());
                }

                if (Config.isRunning) {
                    try {
                        System.out.println(clientId + ": Reconnecting in 5 seconds...");
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            System.out.println(clientId + " Thread Stopped");
        };
    }
}