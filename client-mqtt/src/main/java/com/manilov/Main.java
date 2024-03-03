package com.manilov;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String broker = "tcp://localhost:1883";
        String topic = "metricsTopic";
        String clientIdPrefix = "JavaMqttPublisher";

        for (int i = 0; i < 3; i++) {
            final String clientId = clientIdPrefix + i;

            Runnable task = () -> {
                try (MqttDefaultFilePersistence persistence = new MqttDefaultFilePersistence("tmpFiles");
                     MqttClient client = new MqttClient(broker, clientId, persistence)) {
                    MqttConnectOptions connOpts = new MqttConnectOptions();
                    connOpts.setCleanSession(true);

                    System.out.println("Connecting to broker: " + broker);
                    client.connect(connOpts);
                    System.out.println("Connected");

                    while (!Thread.interrupted()) {
                        String message = String.valueOf(System.currentTimeMillis());
                        MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                        mqttMessage.setQos(0);
                        //System.out.println("Publishing message: " + message);
                        client.publish(topic, mqttMessage);
                        System.out.println("Message published: " + message);
                        Random random = new Random();

                        TimeUnit.SECONDS.sleep(random.nextInt(5) + 3);
                    }
                } catch (InterruptedException | MqttException e) {
                    System.err.println(e.getMessage());
                }
            };
            new Thread(task).start();
        }
    }
}
