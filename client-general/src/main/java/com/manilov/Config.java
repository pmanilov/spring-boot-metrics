package com.manilov;

public class Config {
    public static String hostname = "localhost";
    public static Long period = 1000L;
    public static Integer countClients = 1;
    public static Integer mqttPort = 1884;
    public static String topicMqtt = "metricsTopic";
    public static String clientIdPrefixMqtt = "JavaMqttPublisher";
    public static String topicMqttUdp = "metricsTopic";

    public static String metricsHostMqtt = "localhost";
    public static int metricsPortMqtt = 8081;
    public static String metricsHostMqttUdp = "localhost";
    public static int metricsPortMqttUdp = 8082;

    public static volatile boolean paused = false;
    public static volatile boolean started = false;

    public static String getBrokerUrl() {
        return "tcp://" + hostname + ":" + mqttPort;
    }
}