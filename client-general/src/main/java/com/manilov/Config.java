package com.manilov;

public class Config {
    // Runtime state
    public static volatile boolean isRunning = false;

    // Common experiment parameters
    public static Double intensity = 5.0;
    public static Integer countClients = 1;
    public static int bytesOverhead;

    // Per-protocol settings
    public static final Mqtt mqtt = new Mqtt();
    public static final MqttUdp mqttUdp = new MqttUdp();
    public static final MqttQuic mqttQuic = new MqttQuic();

    public static final class Mqtt {
        public boolean enabled = true;
        public String brokerHost = "localhost";
        public int brokerPort = 1884;
        public String topic = "metricsTopic";
        public String clientIdPrefix = "JavaMqttPublisher";
        public String metricsHost = "localhost";
        public int metricsPort = 8081;
        public volatile int qos = 0;

        public String brokerUrl() {
            return "tcp://" + brokerHost + ":" + brokerPort;
        }
    }

    public static final class MqttUdp {
        public boolean enabled = true;
        // Unicast target for PublishPacket.send(addr). Set to the remote UDP server VPS.
        public String host = "localhost";
        public String topic = "metricsTopic";
        public String metricsHost = "localhost";
        public int metricsPort = 8082;
        public volatile int qos = 0;
    }

    public static final class MqttQuic {
        public boolean enabled = true;
        public String host = "localhost";
        public int port = 14567;
        public String alpn = "mqtt";
        public String topic = "metricsTopic";
        public String clientIdPrefix = "JavaMqttQuicPublisher";
        public String metricsHost = "localhost";
        public int metricsPort = 8083;
        public volatile int qos = 0;
    }
}
