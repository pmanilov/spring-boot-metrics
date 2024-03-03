package com.manilov.servermqtt.configuration;

import com.manilov.servermqtt.service.MetricService;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Pollers;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.stream.CharacterStreamReadingMessageSource;
import org.springframework.messaging.MessageHandler;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    private final MetricService metricService;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{"tcp://localhost:1883"});
        options.setUserName("guest");
        options.setPassword("guest".toCharArray());
        factory.setConnectionOptions(options);
        return factory;
    }

    // publisher

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
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler("metricsPublisher", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic("metricsTopic");
        return messageHandler;
    }

    // consumer

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
            String payload = (String) message.getPayload();
            metricService.updateDelay(Long.parseLong(payload));
        };
    }

    @Bean
    public MessageProducerSupport mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter("metricsConsumer",
                mqttClientFactory(), "metricsTopic");
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(0);
        return adapter;
    }

}
