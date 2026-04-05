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

/**
 * Experiment runner that measures real packet-loss ratio on a real network.
 *
 * No synthetic loss is injected via tc/netem. Loss is induced by driving the
 * broker at very high request rates. For each configured intensity the runner
 * resets the sent counter on the client and the received counter on the
 * server, runs a fixed-duration load, then fetches the two counters and
 * writes the ratio to CSV.
 */
public class PacketLossExperimentRunner {
    private static final int[] CLIENT_COUNTS = { 1 };
    // Much higher intensities than the other experiments, chosen to actually
    // saturate broker/UDP buffers on a real network.
    private static final double[] INTENSITIES = { 10, 100, 500, 1000, 5000, 10000,  15000, 20000,  25000};
    // One duration per intensity: enough time to gather a statistically
    // meaningful number of packets without taking forever at the top end.
    private static final int[] TEST_DURATIONS_SECONDS = { 1200, 600, 600, 600, 600, 300, 300, 300, 300 };
    private static final int WARMUP_DURATION_SECONDS = 300;
    // After stopping the producer, wait so in-flight packets can be delivered
    // and counted on the server before we read the counter.
    private static final int DRAIN_SECONDS = 10;
    private static final String CSV_FILE = "experiment_results_packet_loss.csv";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Protocol,Clients,Intensity(req/sec),Sent,Received,LossRatio,AvgDelay(ms),AvgPacketSize(bytes)");
        } catch (IOException e) {
            System.err.println("Could not create CSV file: " + e.getMessage());
            return;
        }

        warmup();

        for (int clients : CLIENT_COUNTS) {
            for (int i = 0; i < INTENSITIES.length; i++) {
                double intensity = INTENSITIES[i];
                int duration = TEST_DURATIONS_SECONDS[i];
                Config.intensity = intensity;
                Config.countClients = clients;

                runProtocolTest("MQTT", clients, intensity, duration,
                        Config.metricsHostMqtt, Config.metricsPortMqtt);

                runProtocolTest("MQTT-UDP", clients, intensity, duration,
                        Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);

                runProtocolTest("MQTT-QUIC", clients, intensity, duration,
                        Config.metricsHostMqttQuic, Config.metricsPortMqttQuic);
            }
        }

        MqttQuicSender.close();
    }

    private static void runProtocolTest(String protocol, int clients, double intensity,
            int durationSeconds, String metricsHost, int metricsPort) {

        System.out.printf("[%s] Starting... (Clients: %d, Int: %.1f, Dur: %ds)%n",
                protocol, clients, intensity, durationSeconds);

        clearServerMetrics(metricsHost, metricsPort);
        resetServerCount(metricsHost, metricsPort);
        Main.sentCountMqtt.set(0);
        Main.sentCountMqttUdp.set(0);
        Main.sentCountMqttQuic.set(0);

        Config.isRunning = true;
        Config.enableMqtt = protocol.equals("MQTT");
        Config.enableMqttUdp = protocol.equals("MQTT-UDP");
        Config.enableMqttQuic = protocol.equals("MQTT-QUIC");
        Config.countClients = clients;
        Config.bytesOverhead = 0;

        List<Thread> threads = new ArrayList<>();

        if (Config.enableMqtt) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.clientIdPrefixMqtt + "_Loss_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMQTT(clientId));
                threads.add(t);
            }
        } else if (Config.enableMqttUdp) {
            ru.dz.mqtt_udp.Engine.setThrottle(0);
            for (int i = 0; i < Config.countClients; i++) {
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttUdp());
                threads.add(t);
            }
        } else if (Config.enableMqttQuic) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.clientIdPrefixMqttQuic + "_Loss_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId));
                threads.add(t);
            }
        }

        sleepSeconds(durationSeconds);

        Config.isRunning = false;

        for (Thread t : threads) {
            t.interrupt();
        }

        // Allow in-flight packets to be delivered and counted.
        sleepSeconds(DRAIN_SECONDS);

        long sent;
        if (Config.enableMqtt) {
            sent = Main.sentCountMqtt.get();
        } else if (Config.enableMqttUdp) {
            sent = Main.sentCountMqttUdp.get();
        } else {
            sent = Main.sentCountMqttQuic.get();
        }
        long received = getServerCount(metricsHost, metricsPort);
        double lossRatio = sent > 0 ? 1.0 - ((double) received / (double) sent) : 0.0;
        if (lossRatio < 0) {
            lossRatio = 0.0;
        }

        Double avgDelay = getMetric(metricsHost, metricsPort, "delay/avg");
        Double avgPacketSize = getMetric(metricsHost, metricsPort, "packet-size/avg");

        System.out.printf("[%s] Result: Sent=%d, Received=%d, Loss=%.4f, Delay=%.2f ms, Size=%.2f bytes%n",
                protocol, sent, received, lossRatio, avgDelay, avgPacketSize);

        saveToCsv(protocol, clients, intensity, sent, received, lossRatio, avgDelay, avgPacketSize);

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

    private static void resetServerCount(String host, int port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics/count/reset";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("Failed to reset server count: " + e.getMessage());
        }
    }

    private static long getServerCount(String host, int port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics/count";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                return Long.parseLong(response.body().trim());
            }
        } catch (Exception e) {
            System.err.println("Error fetching server count: " + e.getMessage());
        }
        return 0L;
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
                return Double.parseDouble(response.body().trim());
            }
        } catch (Exception e) {
            System.err.println("Error fetching metric " + endpoint + ": " + e.getMessage());
        }
        return 0.0;
    }

    private static void saveToCsv(String protocol, int clients, double intensity, long sent, long received,
            double lossRatio, Double delay, Double size) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%s,%d,%.1f,%d,%d,%.6f,%.4f,%.4f%n",
                    protocol, clients, intensity, sent, received, lossRatio,
                    (delay != null ? delay : 0.0),
                    (size != null ? size : 0.0));
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private static void warmup() {
        System.out.printf("[WARMUP] Starting warm-up phase (%d seconds)...%n", WARMUP_DURATION_SECONDS);

        for (String protocol : new String[] { "MQTT", "MQTT-UDP", "MQTT-QUIC" }) {
            Config.isRunning = true;
            Config.enableMqtt = protocol.equals("MQTT");
            Config.enableMqttUdp = protocol.equals("MQTT-UDP");
            Config.enableMqttQuic = protocol.equals("MQTT-QUIC");
            Config.countClients = 1;
            Config.intensity = 100.0;
            Config.bytesOverhead = 0;

            List<Thread> threads = new ArrayList<>();
            if (Config.enableMqtt) {
                String clientId = Config.clientIdPrefixMqtt + "_LossWarmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(clientId)));
            } else if (Config.enableMqttUdp) {
                ru.dz.mqtt_udp.Engine.setThrottle(0);
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
            } else if (Config.enableMqttQuic) {
                String clientId = Config.clientIdPrefixMqttQuic + "_LossWarmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId)));
            }

            sleepSeconds(WARMUP_DURATION_SECONDS / 3);

            Config.isRunning = false;
            threads.forEach(Thread::interrupt);
        }

        clearServerMetrics(Config.metricsHostMqtt, Config.metricsPortMqtt);
        clearServerMetrics(Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);
        clearServerMetrics(Config.metricsHostMqttQuic, Config.metricsPortMqttQuic);
        resetServerCount(Config.metricsHostMqtt, Config.metricsPortMqtt);
        resetServerCount(Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);
        resetServerCount(Config.metricsHostMqttQuic, Config.metricsPortMqttQuic);

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