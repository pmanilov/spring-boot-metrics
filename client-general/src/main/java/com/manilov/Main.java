package com.manilov;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static final AtomicLong sentCountMqtt = new AtomicLong(0);
    public static final AtomicLong sentCountMqttUdp = new AtomicLong(0);
    public static final AtomicLong sentCountMqttQuic = new AtomicLong(0);

    public static void main(String[] args) {
        if (Config.mqtt.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                final String clientId = Config.mqtt.clientIdPrefix + i;
                Runnable taskMQTT = getTaskMQTT(clientId);
                Thread.startVirtualThread(taskMQTT);
            }
        }

        if (Config.mqttUdp.enabled) {
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

    static Runnable getTaskMqttQuic(String clientId) {
        return () -> {
            String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";
            MqttQuicSender.Session session;
            try {
                session = MqttQuicSender.get().openSession(clientId);
            } catch (Exception e) {
                System.err.println(clientId + " failed to open MQTT-QUIC session: " + e.getMessage());
                return;
            }
            try {
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
                        session.publish(Config.mqttQuic.topic, payload.getBytes());
                        sentCountMqttQuic.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("MQTT-QUIC publish error: " + e.getMessage());
                        break;
                    }
                }
            } finally {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        };
    }

    static Runnable getTaskMqttUdp() {
        return () -> {
            String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";
            final InetAddress target;
            try {
                target = InetAddress.getByName(Config.mqttUdp.host);
            } catch (UnknownHostException e) {
                System.err.println("MQTT-UDP: cannot resolve host " + Config.mqttUdp.host + ": " + e.getMessage());
                return;
            }
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
                    PublishPacket pkt = new PublishPacket(Config.mqttUdp.topic, payload, Config.mqttUdp.qos);
                    pkt.send(target);
                    sentCountMqttUdp.incrementAndGet();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
        };
    }

    static Runnable getTaskMQTT(String clientIdPrefix) {
        return () -> {
            String clientId = clientIdPrefix + "_" + java.util.UUID.randomUUID();
            while (Config.isRunning) {

                try (MemoryPersistence persistence = new MemoryPersistence()) {
                    MqttAsyncClient client = new MqttAsyncClient(Config.mqtt.brokerUrl(), clientId, persistence);

                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(true);
                    connOpts.setKeepAliveInterval(10);
                    connOpts.setAutomaticReconnect(false);
                    connOpts.setMaxInflight(10_000);
                    connOpts.setSocketFactory(new NoDelaySocketFactory());

                    System.out.println(clientId + ": Connecting to broker...");

                    client.connect(connOpts).waitForCompletion();
                    System.out.println(clientId + ": Connected!");

                    String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";

                    try {
                        while (client.isConnected() && Config.isRunning) {
                            TimeUnit.NANOSECONDS.sleep(getNextPoissonDelayNanos(Config.intensity));
                            Instant now = Instant.now();
                            long currentTime = now.getEpochSecond() * 1_000_000_000L + now.getNano();
                            String message = currentTime + overhead;

                            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                            mqttMessage.setQos(Config.mqtt.qos);

                            client.publish(Config.mqtt.topic, mqttMessage).waitForCompletion();
                            sentCountMqtt.incrementAndGet();
                        }
                    } finally {
                        try {
                            if (client.isConnected()) {
                                client.disconnect(30_000).waitForCompletion();
                            }
                        } catch (MqttException ignored) {
                        }
                        try {
                            client.close();
                        } catch (MqttException ignored) {
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (MqttException e) {
                    System.err.println(clientId + " Critical error: " + e.getMessage());
                }

                if (Config.isRunning) {
                    try {
                        System.out.println(clientId + ": Reconnecting in 1 second...");
                        TimeUnit.SECONDS.sleep(1);
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