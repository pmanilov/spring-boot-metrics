package com.manilov.servermqtt.configuration;

import com.manilov.common.service.DelayService;
import com.manilov.servermqtt.handler.PacketSizeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.mqtt.inbound.Mqttv5PahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.Mqttv5PahoMessageHandler;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.integration.stream.CharacterStreamReadingMessageSource;
import org.springframework.messaging.MessageHandler;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {
    private final DelayService delayService;
    private final PacketSizeHandler packetSizeHandler;

    @Value("${mqtt.url}")
    private String url;
    @Value("${server.id}")
    private String serverId;
    @Value("${metrics.topic}")
    private String metricsTopic;

    private MqttConnectionOptions mqttConnectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setServerURIs(new String[] { url });
        options.setCleanStart(true);
        options.setSessionExpiryInterval(0L);
        options.setKeepAliveInterval(30);
        options.setAutomaticReconnect(true);
        options.setSocketFactory(new NoDelaySocketFactory());
        return options;
    }

    @Bean
    public IntegrationFlow mqttOutFlow() {
        return IntegrationFlow.from(CharacterStreamReadingMessageSource.stdin(),
                e -> e.poller(Pollers.fixedDelay(1000)))
                .transform(p -> p + " sent to MQTT")
                .handle(mqttOutbound())
                .get();
    }

    @Bean
    public MessageHandler mqttOutbound() {
        Mqttv5PahoMessageHandler messageHandler =
                new Mqttv5PahoMessageHandler(mqttConnectionOptions(), "metricsPublisher");
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(metricsTopic);
        return messageHandler;
    }

    @Bean
    public IntegrationFlow mqttInFlow() {
        return IntegrationFlow.from(mqttInbound())
                .handle(messageHandler())
                .get();
    }

    private LoggingHandler logger() {
        LoggingHandler loggingHandler = new LoggingHandler("INFO");
        loggingHandler.setLoggerName("metricsLogger");
        return loggingHandler;
    }

    @Bean
    public MessageHandler messageHandler() {
        return message -> {
            byte[] payloadBytes = payloadBytes(message.getPayload());
            String payloadStr = new String(payloadBytes, StandardCharsets.UTF_8);
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
                String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
                packetSizeHandler.handleMessage(topic != null ? topic : metricsTopic, payloadBytes);
            } catch (Exception e) {
                log.warn("packetSizeHandler failed: {}", e.getMessage());
            }
        };
    }

    @Bean
    public MessageProducerSupport mqttInbound() {
        Mqttv5PahoMessageDrivenChannelAdapter adapter =
                new Mqttv5PahoMessageDrivenChannelAdapter(mqttConnectionOptions(), "metricsConsumer", metricsTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setQos(2);
        return adapter;
    }

    private byte[] payloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8);
    }

}
