package com.manilov;

public class Config {
    public static String hostname = "localhost";
    public static Long period = 400L;
    public static Integer countClients = 5;
    public static Integer mqttPort = 1884;
    public static String topicMqtt = "metricsTopic";
    public static String clientIdPrefixMqtt = "JavaMqttPublisher";
    public static String topicMqttUdp = "metricsTopic";

    public static String getBrokerUrl() {
        return "tcp://" + hostname + ":" + mqttPort;
    }
}