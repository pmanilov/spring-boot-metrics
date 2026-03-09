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
    private static final int[] BYTES_OVERHEADS = {0, 10000, 20000, 30000, 40000, 50000, 60000};
    private static final int[] CLIENT_COUNTS = { 1 };
    private static final double[] INTENSITIES = { 1, 5, 10, 20, 30, 40, 50 };

    private static final int TEST_DURATION_SECONDS = 300;
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
                for (double intensity : INTENSITIES) {
                    Config.intensity = intensity;
                    Config.countClients = clients;
                    runProtocolTest("MQTT", overhead, clients, intensity,
                            Config.metricsHostMqtt, Config.metricsPortMqtt);

                    sleepSeconds(2);

                    runProtocolTest("MQTT-UDP", overhead, clients, intensity,
                            Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);
                }
            }
        }
    }

    private static void runProtocolTest(String protocol, int overhead, int clients, double intensity,
            String metricsHost, int metricsPort) {

        System.out.printf("[%s] Starting... (Overhead: %d, Clients: %d, Int: %.1f)\n",
                protocol, overhead, clients, intensity);

        clearServerMetrics(metricsHost, metricsPort);

        Config.isRunning = true;
        Config.enableMqtt = protocol.equals("MQTT");
        Config.enableMqttUdp = protocol.equals("MQTT-UDP");
        Config.countClients = clients;
        Config.bytesOverhead = overhead;

        List<Thread> threads = new ArrayList<>();

        if (Config.enableMqtt) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.clientIdPrefixMqtt + "_Test_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMQTT(clientId));
                threads.add(t);
            }
        } else if (Config.enableMqttUdp) {
            ru.dz.mqtt_udp.Engine.setThrottle(0);
            for (int i = 0; i < Config.countClients; i++) {
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttUdp());
                threads.add(t);
            }
        }

        sleepSeconds(TEST_DURATION_SECONDS);

        Config.isRunning = false;

        for (Thread t : threads) {
            t.interrupt();
        }

        Double avgDelay = getMetric(metricsHost, metricsPort, "delay/avg");
        Double avgPacketSize = getMetric(metricsHost, metricsPort, "packet-size/avg");

        System.out.printf("[%s] Result: Delay=%.2f ms, Size=%.2f bytes\n", protocol, avgDelay, avgPacketSize);

        saveToCsv(protocol, overhead, clients, intensity, avgDelay, avgPacketSize);
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

        for (String protocol : new String[] { "MQTT", "MQTT-UDP" }) {
            Config.isRunning = true;
            Config.enableMqtt = protocol.equals("MQTT");
            Config.enableMqttUdp = protocol.equals("MQTT-UDP");
            Config.countClients = 1;
            Config.intensity = 10.0;
            Config.bytesOverhead = 0;

            List<Thread> threads = new ArrayList<>();
            if (Config.enableMqtt) {
                String clientId = Config.clientIdPrefixMqtt + "_Warmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(clientId)));
            } else {
                ru.dz.mqtt_udp.Engine.setThrottle(0);
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
            }

            sleepSeconds(WARMUP_DURATION_SECONDS / 2);

            Config.isRunning = false;
            threads.forEach(Thread::interrupt);
        }

        // Clear server-side metrics accumulated during warm-up
        clearServerMetrics(Config.metricsHostMqtt, Config.metricsPortMqtt);
        clearServerMetrics(Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);

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