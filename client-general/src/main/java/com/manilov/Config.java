package com.manilov;

public class Config {
    public static String hostname = "localhost";

    public static Double intensity = 5.0;

    public static Integer countClients = 1;

    public static boolean enableMqtt = true;
    public static boolean enableMqttUdp = true;

    public static Integer mqttPort = 1884;
    public static String topicMqtt = "metricsTopic";
    public static String clientIdPrefixMqtt = "JavaMqttPublisher";
    public static String topicMqttUdp = "metricsTopic";

    public static String metricsHostMqtt = "localhost";
    public static int metricsPortMqtt = 8081;
    public static String metricsHostMqttUdp = "localhost";
    public static int metricsPortMqttUdp = 8082;

    public static volatile boolean isRunning = false;

    public static String getBrokerUrl() {
        return "tcp://" + hostname + ":" + mqttPort;
    }

    public static int bytesOverhead;
}