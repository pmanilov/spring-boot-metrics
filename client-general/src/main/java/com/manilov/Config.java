package com.manilov;

public class Config {
    public static String hostname = "localhost";

    public static Double intensity = 5.0;

    public static Integer countClients = 1;

    public static boolean enableMqtt = true;
    public static boolean enableMqttUdp = true;
    public static boolean enableMqttQuic = true;

    public static Integer mqttPort = 1884;
    public static String topicMqtt = "metricsTopic";
    public static String clientIdPrefixMqtt = "JavaMqttPublisher";
    public static String topicMqttUdp = "metricsTopic";
    public static String topicMqttQuic = "metricsTopic";
    public static String clientIdPrefixMqttQuic = "JavaMqttQuicPublisher";

    public static String metricsHostMqtt = "localhost";
    public static int metricsPortMqtt = 8081;
    public static String metricsHostMqttUdp = "localhost";
    public static int metricsPortMqttUdp = 8082;
    public static String metricsHostMqttQuic = "localhost";
    public static int metricsPortMqttQuic = 8083;

    public static String quicHost = "localhost";
    public static int quicPort = 1885;
    public static String quicAlpn = "mqtt";

    public static volatile boolean isRunning = false;

    public static String getBrokerUrl() {
        return "tcp://" + hostname + ":" + mqttPort;
    }

    public static int bytesOverhead;
}