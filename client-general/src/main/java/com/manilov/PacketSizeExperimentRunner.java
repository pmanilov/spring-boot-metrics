package com.manilov;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PacketSizeExperimentRunner {
    private static final int[] BYTES_OVERHEADS = { 0, 10000, 20000, 30000, 40000, 50000, 60000 };
    private static final int[] CLIENT_COUNTS = { 1 };
    private static final double[] INTENSITIES = { 5, 10, 20, 30, 40, 50 };
    private static final int[] TEST_DURATIONS_SECONDS = { 3000, 1500, 750, 500, 500, 500};
    private static final int WARMUP_DURATION_SECONDS = 600;
    private static final String CSV_FILE = "experiment_results_packet_size.csv";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Protocol,Overhead(bytes),Clients,Intensity(req/sec),AvgDelay(ms),AvgPacketSize(bytes)");
        } catch (IOException e) {
            System.err.println("Could not create CSV file: " + e.getMessage());
            return;
        }

        warmup();

        for (int overhead : BYTES_OVERHEADS) {
            for (int clients : CLIENT_COUNTS) {
                for (int i = 0; i < INTENSITIES.length; i++) {
                    double intensity = INTENSITIES[i];
                    int duration = TEST_DURATIONS_SECONDS[i];
                    Config.intensity = intensity;
                    Config.countClients = clients;
                    runProtocolTest("MQTT", overhead, clients, intensity, duration,
                            Config.mqtt.metricsHost, Config.mqtt.metricsPort);

                    runProtocolTest("MQTT-UDP", overhead, clients, intensity, duration,
                            Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);

                    runProtocolTest("MQTT-QUIC", overhead, clients, intensity, duration,
                            Config.mqttQuic.metricsHost, Config.mqttQuic.metricsPort);
                }
            }
        }

        MqttQuicSender.close();
    }

    private static void runProtocolTest(String protocol, int overhead, int clients, double intensity,
            int durationSeconds, String metricsHost, int metricsPort) {

        System.out.printf("[%s] Starting... (Overhead: %d, Clients: %d, Int: %.1f)\n",
                protocol, overhead, clients, intensity);

        clearServerMetrics(metricsHost, metricsPort);

        Config.isRunning = true;
        Config.mqtt.enabled = protocol.equals("MQTT");
        Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
        Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
        Config.countClients = clients;
        Config.bytesOverhead = overhead;

        List<Thread> threads = new ArrayList<>();

        if (Config.mqtt.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.mqtt.clientIdPrefix + "_Test_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMQTT(clientId));
                threads.add(t);
            }
        } else if (Config.mqttUdp.enabled) {
            ru.dz.mqtt_udp.Engine.setThrottle(0);
            for (int i = 0; i < Config.countClients; i++) {
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttUdp());
                threads.add(t);
            }
        } else if (Config.mqttQuic.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.mqttQuic.clientIdPrefix + "_Test_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId));
                threads.add(t);
            }
        }

        sleepSeconds(durationSeconds);

        Config.isRunning = false;

        for (Thread t : threads) {
            t.interrupt();
        }

        Double avgDelay = getMetric(metricsHost, metricsPort, "delay/avg");
        Double avgPacketSize = getMetric(metricsHost, metricsPort, "packet-size/avg");

        System.out.printf("[%s] Result: Delay=%.2f ms, Size=%.2f bytes\n", protocol, avgDelay, avgPacketSize);

        saveToCsv(protocol, overhead, clients, intensity, avgDelay, avgPacketSize);

        sleepSeconds(5);
    }

    private static void clearServerMetrics(String host, int port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics/delete";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Failed to clear metrics: " + e.getMessage());
        }
    }

    private static Double getMetric(String host, int port, String endpoint) {
        try {
            String url = "http://" + host + ":" + port + "/metrics/" + endpoint;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                return Double.parseDouble(response.body());
            }
        } catch (Exception e) {
            System.err.println("Error fetching metric " + endpoint + ": " + e.getMessage());
        }
        return 0.0;
    }

    private static void saveToCsv(String protocol, int overhead, int clients, double intensity, Double delay,
            Double size) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%s,%d,%d,%.1f,%.4f,%.4f%n",
                    protocol, overhead, clients, intensity,
                    (delay != null ? delay : 0.0),
                    (size != null ? size : 0.0));
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private static void warmup() {
        System.out.printf("[WARMUP] Starting warm-up phase (%d minutes)...%n", WARMUP_DURATION_SECONDS / 60);

        for (String protocol : new String[] { "MQTT", "MQTT-UDP", "MQTT-QUIC" }) {
            Config.isRunning = true;
            Config.mqtt.enabled = protocol.equals("MQTT");
            Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
            Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
            Config.countClients = 1;
            Config.intensity = 10.0;
            Config.bytesOverhead = 0;

            List<Thread> threads = new ArrayList<>();
            if (Config.mqtt.enabled) {
                String clientId = Config.mqtt.clientIdPrefix + "_Warmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(clientId)));
            } else if (Config.mqttUdp.enabled) {
                ru.dz.mqtt_udp.Engine.setThrottle(0);
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
            } else if (Config.mqttQuic.enabled) {
                String clientId = Config.mqttQuic.clientIdPrefix + "_Warmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId)));
            }

            sleepSeconds(WARMUP_DURATION_SECONDS / 3);

            Config.isRunning = false;
            threads.forEach(Thread::interrupt);
        }

        clearServerMetrics(Config.mqtt.metricsHost, Config.mqtt.metricsPort);
        clearServerMetrics(Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);
        clearServerMetrics(Config.mqttQuic.metricsHost, Config.mqttQuic.metricsPort);

        System.out.println("[WARMUP] Done. Starting main experiment.");
    }

    private static void sleepSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}