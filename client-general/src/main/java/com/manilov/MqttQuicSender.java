package com.manilov;

import com.manilov.common.mqttquic.MqttQuicClient;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.concurrent.CompletableFuture;

/**
 * Тонкая обёртка над {@link MqttQuicClient}. По одному экземпляру на издателя —
 * каждый получает собственный UDP-сокет и event loop, как у Paho в MQTT/TCP.
 * Singleton'а здесь специально нет: общий клиент сериализует QUIC-обработку
 * на одном NIO-треде и одном UDP-канале, что становится узким местом при
 * нескольких одновременных издателях (см. CodeReview подраздел C).
 */
public final class MqttQuicSender implements AutoCloseable {

    private final MqttQuicClient client;

    public MqttQuicSender(String host, int port, String alpn) throws Exception {
        this.client = new MqttQuicClient(host, port, alpn);
    }

    public Session openSession(String clientId) throws Exception {
        return new Session(client.openSession(clientId));
    }

    @Override
    public void close() {
        client.close();
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
