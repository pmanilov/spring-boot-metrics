package com.manilov.servermqttquic.configuration;

import com.manilov.common.mqttquic.MqttQuicClient;
import com.manilov.common.service.DelayService;
import com.manilov.servermqttquic.handler.PacketSizeHandler;
import io.netty.handler.codec.mqtt.MqttQoS;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttQuicSubscriberConfiguration {
    private static final int PING_INTERVAL_SECONDS = 20;

    private final DelayService delayService;
    private final PacketSizeHandler packetSizeHandler;

    @Value("${server.id}")
    private String serverId;
    @Value("${metrics.topic}")
    private String metricsTopic;
    @Value("${emqx.host}")
    private String emqxHost;
    @Value("${emqx.port}")
    private int emqxPort;
    @Value("${emqx.alpn}")
    private String emqxAlpn;
    @Value("${emqx.client-id}")
    private String clientId;

    private volatile boolean running;
    private volatile boolean subscribed;
    private volatile MqttQuicClient client;
    private volatile MqttQuicClient.Session session;

    /**
     * True between successful SUBACK and session close. Используется
     * /metrics/ready, чтобы раннер мог дождаться готовности подписчика
     * перед стартом publishers — иначе при QoS 0 и низком λ часть стартовых
     * сообщений теряется (MQUEUE_STORE_QOS0=false на брокере).
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * Принудительно роняет текущую QUIC-сессию. runLoop увидит закрытие,
     * пройдёт finally и переподключится с clean_start=true — EMQX
     * выкинет inflight/awaiting_rel/mqueue прошлой сессии. Используется
     * раннером эксперимента между прогонами матрицы, иначе session-state
     * копится в одной живой сессии и под нагрузкой clients>=10 валит
     * подписчика.
     */
    public void recycle() {
        log.info("Recycling MQTT-QUIC subscriber session by request");
        subscribed = false;
        closeSession();
    }
    private ExecutorService executor;
    private ExecutorService messageExecutor;
    private ScheduledExecutorService keepAliveExecutor;
    private ScheduledFuture<?> keepAliveFuture;

    @PostConstruct
    public void start() {
        running = true;
        executor = Executors.newSingleThreadExecutor();
        messageExecutor = Executors.newVirtualThreadPerTaskExecutor();
        keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
        executor.submit(this::runLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        closeSession();
        closeClient();
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (messageExecutor != null) {
            messageExecutor.shutdownNow();
            try {
                messageExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (keepAliveExecutor != null) {
            keepAliveExecutor.shutdownNow();
            try {
                keepAliveExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop() {
        while (running) {
            try {
                ensureClient();
                MqttQuicClient.Session activeSession = client.openSession(clientId);
                session = activeSession;
                activeSession.setMessageListener((topic, payload, qos, duplicate, packetId) -> {
                    try {
                        if (messageExecutor != null && !messageExecutor.isShutdown()) {
                            messageExecutor.submit(() -> handleMessage(topic, payload));
                        }
                    } catch (RuntimeException e) {
                        log.warn("Failed to dispatch MQTT-QUIC message for processing: {}", e.getMessage());
                    }
                });
                activeSession.subscribe(metricsTopic, MqttQoS.EXACTLY_ONCE);
                subscribed = true;
                startKeepAlive(activeSession);
                log.info("Connected to EMQX MQTT-over-QUIC broker {}:{} and subscribed to '{}'",
                        emqxHost, emqxPort, metricsTopic);
                activeSession.closedFuture().join();
            } catch (Exception e) {
                if (running) {
                    log.warn("MQTT-QUIC subscriber loop failed: {}", rootMessage(e));
                }
            } finally {
                subscribed = false;
                stopKeepAlive();
                closeSession();
                closeClient();
            }

            if (running) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void ensureClient() throws Exception {
        if (client == null) {
            client = new MqttQuicClient(emqxHost, emqxPort, emqxAlpn);
        }
    }

    private void handleMessage(String topic, byte[] payload) {
        if (!metricsTopic.equals(topic)) {
            return;
        }

        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        long sentTs;
        try {
            sentTs = Long.parseLong(payloadStr.split(",")[0]);
        } catch (NumberFormatException e) {
            log.warn("Unparseable metrics payload '{}': {}", payloadStr, e.getMessage());
            return;
        }

        try {
            delayService.save(sentTs, serverId);
        } catch (Exception e) {
            log.warn("delayService.save failed: {}", e.getMessage());
        }

        try {
            packetSizeHandler.handleMessage(topic, payload);
        } catch (Exception e) {
            log.warn("packetSizeHandler failed: {}", e.getMessage());
        }
    }

    private void closeSession() {
        MqttQuicClient.Session current = session;
        session = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                log.debug("Ignoring MQTT-QUIC session close failure: {}", e.getMessage());
            }
        }
    }

    private void startKeepAlive(MqttQuicClient.Session activeSession) {
        stopKeepAlive();
        if (keepAliveExecutor == null || keepAliveExecutor.isShutdown()) {
            return;
        }
        keepAliveFuture = keepAliveExecutor.scheduleAtFixedRate(() -> {
            if (!running || session != activeSession) {
                return;
            }
            try {
                activeSession.ping();
            } catch (Exception e) {
                log.debug("MQTT-QUIC keepalive ping failed: {}", e.getMessage());
            }
        }, PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopKeepAlive() {
        ScheduledFuture<?> current = keepAliveFuture;
        keepAliveFuture = null;
        if (current != null) {
            current.cancel(true);
        }
    }

    private void closeClient() {
        MqttQuicClient current = client;
        client = null;
        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                log.debug("Ignoring MQTT-QUIC client close failure: {}", e.getMessage());
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }
}
