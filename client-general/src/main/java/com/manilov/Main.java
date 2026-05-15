package com.manilov;

import io.netty.handler.codec.mqtt.MqttQoS;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttActionListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import ru.dz.mqtt_udp.Engine;
import ru.dz.mqtt_udp.PublishPacket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

        if (Config.mqttQuic.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                final String clientId = Config.mqttQuic.clientIdPrefix + i;
                Thread.startVirtualThread(getTaskMqttQuic(clientId));
            }
        }
    }

    static long getNextPoissonDelayNanos(double lambda) {
        if (lambda <= 0) return Long.MAX_VALUE;

        double random = ThreadLocalRandom.current().nextDouble();
        double intervalSeconds = -Math.log(1.0 - random) / lambda;

        return (long) (intervalSeconds * 1_000_000_000L);
    }

    private static boolean qosRequiresAck(int qos) {
        return qos > 0;
    }

    private static int maxInFlight(int configured) {
        return Math.max(1, configured);
    }

    private static boolean acquirePermitWhileRunning(Semaphore inFlight,
                                                     AtomicReference<Throwable> publishFailure)
            throws InterruptedException {
        while (Config.isRunning && publishFailure.get() == null) {
            if (inFlight.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    private static boolean drainInFlight(Semaphore inFlight, int maxInFlight, int timeoutSeconds)
            throws InterruptedException {
        int acquired = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.max(0, timeoutSeconds));
        try {
            while (acquired < maxInFlight) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || !inFlight.tryAcquire(remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
                acquired++;
            }
            return true;
        } finally {
            if (acquired > 0) {
                inFlight.release(acquired);
            }
        }
    }

    private static void recordPublishFailure(AtomicReference<Throwable> publishFailure, Throwable error) {
        publishFailure.compareAndSet(null,
                error != null ? error : new IllegalStateException("Unknown publish failure"));
    }

    private static void logPublishFailure(String protocol, AtomicReference<Throwable> publishFailure) {
        Throwable failure = publishFailure.get();
        if (failure != null) {
            System.err.println(protocol + " publish failed: " + failure.getMessage());
        }
    }

    static Runnable getTaskMqttQuic(String clientId) {
        return () -> {
            String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";
            int qosValue = Config.mqttQuic.qos;
            MqttQoS qos = MqttQoS.valueOf(qosValue);
            int maxInFlight = maxInFlight(Config.mqttQuic.maxInFlight);
            Semaphore inFlight = new Semaphore(maxInFlight);
            AtomicReference<Throwable> publishFailure = new AtomicReference<>();
            MqttQuicSender sender;
            MqttQuicSender.Session session;
            try {
                sender = new MqttQuicSender(
                        Config.mqttQuic.host, Config.mqttQuic.port, Config.mqttQuic.alpn);
                session = sender.openSession(clientId);
            } catch (Exception e) {
                System.err.println(clientId + " failed to open MQTT-QUIC session: " + e.getMessage());
                return;
            }
            try {
                while (!Thread.interrupted() && Config.isRunning) {
                    if (publishFailure.get() != null) {
                        break;
                    }
                    try {
                        TimeUnit.NANOSECONDS.sleep(getNextPoissonDelayNanos(Config.intensity));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    Instant now = Instant.now();
                    long currentTime = now.toEpochMilli() / 1_000 * 1_000_000_000 + now.getNano();
                    String payload = currentTime + overhead;
                    boolean permitAcquired = false;
                    try {
                        byte[] payloadBytes = payload.getBytes();
                        if (qosRequiresAck(qosValue)) {
                            if (!acquirePermitWhileRunning(inFlight, publishFailure)) {
                                break;
                            }
                            permitAcquired = true;
                            if (!Config.isRunning) {
                                inFlight.release();
                                permitAcquired = false;
                                break;
                            }
                            CompletableFuture<Void> publishFuture =
                                    session.publishAsync(Config.mqttQuic.topic, payloadBytes, qos);
                            permitAcquired = false;
                            publishFuture.whenComplete((ignored, error) -> {
                                try {
                                    if (error == null) {
                                        sentCountMqttQuic.incrementAndGet();
                                    } else {
                                        recordPublishFailure(publishFailure, error);
                                    }
                                } finally {
                                    inFlight.release();
                                }
                            });
                        } else {
                            session.publish(Config.mqttQuic.topic, payloadBytes, qos);
                            sentCountMqttQuic.incrementAndGet();
                        }
                    } catch (Exception e) {
                        if (permitAcquired) {
                            inFlight.release();
                        }
                        System.err.println("MQTT-QUIC publish error: " + e.getMessage());
                        break;
                    }
                }
            } finally {
                if (qosRequiresAck(qosValue)) {
                    try {
                        if (!drainInFlight(inFlight, maxInFlight, Config.mqttQuic.publishDrainTimeoutSeconds)) {
                            System.err.println("MQTT-QUIC publish drain timed out");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    logPublishFailure("MQTT-QUIC", publishFailure);
                }
                try {
                    session.close();
                } catch (Exception ignored) {
                }
                try {
                    sender.close();
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

                try {
                    MemoryPersistence persistence = new MemoryPersistence();
                    MqttAsyncClient client = new MqttAsyncClient(Config.mqtt.brokerUrl(), clientId, persistence);

                    MqttConnectionOptions connOpts = new MqttConnectionOptions();
                    connOpts.setCleanStart(true);
                    connOpts.setSessionExpiryInterval(0L);
                    connOpts.setKeepAliveInterval(10);
                    connOpts.setAutomaticReconnect(false);
                    connOpts.setSocketFactory(new NoDelaySocketFactory());

                    System.out.println(clientId + ": Connecting to broker...");

                    client.connect(connOpts).waitForCompletion();
                    System.out.println(clientId + ": Connected!");

                    String overhead = Config.bytesOverhead > 0 ? "," + "0".repeat(Config.bytesOverhead - 1) : "";
                    int qosValue = Config.mqtt.qos;
                    int maxInFlight = maxInFlight(Config.mqtt.maxInFlight);
                    Semaphore inFlight = new Semaphore(maxInFlight);
                    AtomicReference<Throwable> publishFailure = new AtomicReference<>();

                    try {
                        while (client.isConnected() && Config.isRunning) {
                            if (publishFailure.get() != null) {
                                break;
                            }
                            TimeUnit.NANOSECONDS.sleep(getNextPoissonDelayNanos(Config.intensity));
                            Instant now = Instant.now();
                            long currentTime = now.getEpochSecond() * 1_000_000_000L + now.getNano();
                            String message = currentTime + overhead;

                            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                            mqttMessage.setQos(qosValue);

                            boolean permitAcquired = false;
                            try {
                                if (qosRequiresAck(qosValue)) {
                                    if (!acquirePermitWhileRunning(inFlight, publishFailure)) {
                                        break;
                                    }
                                    permitAcquired = true;
                                    if (!Config.isRunning) {
                                        inFlight.release();
                                        permitAcquired = false;
                                        break;
                                    }
                                    client.publish(Config.mqtt.topic, mqttMessage, null, new MqttActionListener() {
                                        @Override
                                        public void onSuccess(IMqttToken asyncActionToken) {
                                            try {
                                                sentCountMqtt.incrementAndGet();
                                            } finally {
                                                inFlight.release();
                                            }
                                        }

                                        @Override
                                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                            try {
                                                recordPublishFailure(publishFailure, exception);
                                            } finally {
                                                inFlight.release();
                                            }
                                        }
                                    });
                                    permitAcquired = false;
                                } else {
                                    client.publish(Config.mqtt.topic, mqttMessage).waitForCompletion();
                                    sentCountMqtt.incrementAndGet();
                                }
                            } catch (MqttException e) {
                                if (permitAcquired) {
                                    inFlight.release();
                                }
                                throw e;
                            }
                        }
                    } finally {
                        if (qosRequiresAck(qosValue)) {
                            try {
                                if (!drainInFlight(inFlight, maxInFlight, Config.mqtt.publishDrainTimeoutSeconds)) {
                                    System.err.println("MQTT publish drain timed out");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            logPublishFailure("MQTT", publishFailure);
                        }
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
