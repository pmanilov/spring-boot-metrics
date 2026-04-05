package com.manilov;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static final AtomicLong sentCountMqtt = new AtomicLong(0);
    public static final AtomicLong sentCountMqttUdp = new AtomicLong(0);

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

    static long getNextPoissonDelayNanos(double lambda) {
        if (lambda <= 0) return Long.MAX_VALUE;

        double random = ThreadLocalRandom.current().nextDouble();
        double intervalSeconds = -Math.log(1.0 - random) / lambda;

        return (long) (intervalSeconds * 1_000_000_000L);
    }

    static Runnable getTaskMqttUdp() {
        return () -> {
            String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";
            while (!Thread.interrupted() && Config.isRunning) {
                try {
                    TimeUnit.NANOSECONDS.sleep(getNextPoissonDelayNanos(Config.intensity));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Instant now = Instant.now();
                long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                String payload = currentTime + overhead;
                try {
                    PublishPacket pkt = new PublishPacket(Config.topicMqttUdp, payload);
                    pkt.send();
                    sentCountMqttUdp.incrementAndGet();
                    //System.out.println("MQTT-UDP sent: " + payload);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        };
    }

    static Runnable getTaskMQTT(String clientId) {
        return () -> {
            while (!Thread.interrupted() && Config.isRunning) {

                try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles/" + clientId);
                     MqttClient client = new MqttClient(Config.getBrokerUrl(), clientId, persistence)) {

                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(true);

                    connOpts.setKeepAliveInterval(5);

                    connOpts.setAutomaticReconnect(false);

                    System.out.println(clientId + ": Connecting to broker...");


                    client.connect(connOpts);
                    System.out.println(clientId + ": Connected!");

                    String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";

                    while (client.isConnected() && Config.isRunning) {
                        TimeUnit.NANOSECONDS.sleep(getNextPoissonDelayNanos(Config.intensity));
                        Instant now = Instant.now();
                        long currentTime = now.getEpochSecond() * 1_000_000_000L + now.getNano();
                        String message = currentTime + overhead;

                        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                        mqttMessage.setQos(0);

                        client.publish(Config.topicMqtt, mqttMessage);
                        sentCountMqtt.incrementAndGet();
                        //System.out.println(clientId + " published: " + message);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (MqttException e) {
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