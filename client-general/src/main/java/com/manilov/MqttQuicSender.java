package com.manilov;

import com.manilov.common.mqttquic.MqttQuicClient;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.concurrent.CompletableFuture;

public final class MqttQuicSender {

    private static volatile MqttQuicSender INSTANCE;

    private final String host;
    private final int port;
    private final String alpn;
    private final MqttQuicClient client;

    private MqttQuicSender(String host, int port, String alpn) throws Exception {
        this.host = host;
        this.port = port;
        this.alpn = alpn;
        this.client = new MqttQuicClient(host, port, alpn);
    }

    public static MqttQuicSender get() {
        MqttQuicSender local = INSTANCE;
        if (local != null && local.matchesCurrentConfig()) {
            return local;
        }
        synchronized (MqttQuicSender.class) {
            if (INSTANCE != null && !INSTANCE.matchesCurrentConfig()) {
                INSTANCE.client.close();
                INSTANCE = null;
            }
            if (INSTANCE == null) {
                try {
                    INSTANCE = new MqttQuicSender(Config.mqttQuic.host, Config.mqttQuic.port, Config.mqttQuic.alpn);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize QUIC transport", e);
                }
            }
            return INSTANCE;
        }
    }

    public static synchronized void close() {
        if (INSTANCE != null) {
            INSTANCE.client.close();
            INSTANCE = null;
        }
    }

    public Session openSession(String clientId) throws Exception {
        return new Session(client.openSession(clientId));
    }

    private boolean matchesCurrentConfig() {
        return host.equals(Config.mqttQuic.host)
                && port == Config.mqttQuic.port
                && alpn.equals(Config.mqttQuic.alpn);
    }

    public static final class Session implements AutoCloseable {
        private final MqttQuicClient.Session delegate;

        Session(MqttQuicClient.Session delegate) {
            this.delegate = delegate;
        }

        public void publish(String topic, byte[] payload) {
            delegate.publish(topic, payload);
        }

        public void publish(String topic, byte[] payload, MqttQoS qos) {
            delegate.publish(topic, payload, qos);
        }

        public CompletableFuture<Void> publishAsync(String topic, byte[] payload, MqttQoS qos) {
            return delegate.publishAsync(topic, payload, qos);
        }

        public void subscribe(String topic, MqttQoS qos) {
            delegate.subscribe(topic, qos);
        }

        public CompletableFuture<Void> closedFuture() {
            return delegate.closedFuture();
        }

        public void ping() {
            delegate.ping();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
