package com.manilov;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int[] CLIENT_COUNTS = { 1, 10, 50, 100};
    private static final int[] PAYLOAD_SIZES = { 0, 1024, 2048, 4096, 16384};
    private static final double INTENSITY_START = 500;
    private static final double INTENSITY_END = 10000;
    private static final double INTENSITY_STEP = 500;

    // Total packets per test (to keep total packets constant across different intensities).
    // Increased for longer, more stable runs now that QUIC is excluded.
    private static final int TOTAL_PACKETS_PER_TEST = 200000;
    private static final int MIN_DURATION_SECONDS = 30;
    
    private static final int WARMUP_DURATION_SECONDS = 600;
    // After stopping the producer, wait so in-flight packets can be delivered
    // and counted on the server before we read the counter.
    private static final int DRAIN_SECONDS = 30;
    private static final String CSV_FILE = "experiment_results_packet_loss.csv";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        Set<String> completed = loadCompletedRuns();
        boolean resuming = !completed.isEmpty();

        if (!resuming) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
                writer.println("Protocol,Clients,Overhead(bytes),Intensity(req/sec),Sent,Received,LossRatio,AvgDelay(ms),AvgPacketSize(bytes)");
            } catch (IOException e) {
                System.err.println("Could not create CSV file: " + e.getMessage());
                return;
            }
            warmup();
        } else {
            System.out.printf("[RESUME] Found %d completed runs in %s, skipping warmup.%n",
                    completed.size(), CSV_FILE);
        }

        for (int clients : CLIENT_COUNTS) {
            for (int overhead : PAYLOAD_SIZES) {
                for (double intensity = INTENSITY_START; intensity <= INTENSITY_END; intensity += INTENSITY_STEP) {
                    int duration = Math.max(MIN_DURATION_SECONDS,
                            (int) (TOTAL_PACKETS_PER_TEST / intensity));
                    Config.intensity = intensity / clients; // Distribute load across clients
                    Config.countClients = clients;

                    runIfNeeded(completed, "MQTT", clients, overhead, intensity, duration,
                            Config.mqtt.metricsHost, Config.mqtt.metricsPort);

                    runIfNeeded(completed, "MQTT-UDP", clients, overhead, intensity, duration,
                            Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);
                }
            }
        }
    }

    private static String runKey(String protocol, int clients, int overhead, double intensity) {
        return String.format("%s|%d|%d|%.1f", protocol, clients, overhead, intensity);
    }

    private static Set<String> loadCompletedRuns() {
        Set<String> done = new HashSet<>();
        File f = new File(CSV_FILE);
        if (!f.isFile()) {
            return done;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                if (line.isBlank()) continue;
                String[] p = line.split(",");
                if (p.length < 6) continue;
                try {
                    long sent = Long.parseLong(p[4]);
                    long received = Long.parseLong(p[5]);
                    // Treat rows with zero sent or zero received as failed and re-run them.
                    if (sent == 0 || received == 0) continue;
                    done.add(runKey(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                            Double.parseDouble(p[3])));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            System.err.println("Could not read existing CSV: " + e.getMessage());
        }
        return done;
    }

    private static void runIfNeeded(Set<String> completed, String protocol, int clients, int overhead,
            double intensity, int durationSeconds, String metricsHost, int metricsPort) {
        String key = runKey(protocol, clients, overhead, intensity);
        if (completed.contains(key)) {
            System.out.printf("[%s] Skip (already done): Clients=%d, Overhead=%dB, Int=%.1f%n",
                    protocol, clients, overhead, intensity);
            return;
        }
        runProtocolTest(protocol, clients, overhead, intensity, durationSeconds, metricsHost, metricsPort);
        completed.add(key);
    }

    private static void runProtocolTest(String protocol, int clients, int overhead, double intensity,
            int durationSeconds, String metricsHost, int metricsPort) {

        System.out.printf("[%s] Starting... (Clients: %d, Overhead: %dB, Int: %.1f, Dur: %ds)%n",
                protocol, clients, overhead, intensity, durationSeconds);

        clearServerMetrics(metricsHost, metricsPort);
        resetServerCount(metricsHost, metricsPort);
        Main.sentCountMqtt.set(0);
        Main.sentCountMqttUdp.set(0);
        Main.sentCountMqttQuic.set(0);

        Config.isRunning = true;
        Config.mqtt.enabled = protocol.equals("MQTT");
        Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
        Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
        Config.countClients = clients;
        Config.bytesOverhead = overhead;

        List<Thread> threads = new ArrayList<>();

        if (Config.mqtt.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.mqtt.clientIdPrefix + "_Loss_" + i;
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
                String clientId = Config.mqttQuic.clientIdPrefix + "_Loss_" + i;
                Thread t = Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId));
                threads.add(t);
            }
        }

        sleepSeconds(durationSeconds);

        Config.isRunning = false;

        // Wait for publisher threads to exit their loop and flush any pending
        // messages through a graceful disconnect. Do NOT interrupt() here — that
        // would trip InterruptedException in the sleep() inside the publish
        // loop and skip the disconnect-with-wait, causing the Paho outbound
        // buffer to be discarded on TCP.
        for (Thread t : threads) {
            try {
                t.join(TimeUnit.SECONDS.toMillis(35));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Allow in-flight packets to be delivered and counted.
        sleepSeconds(DRAIN_SECONDS);

        long sent;
        if (Config.mqtt.enabled) {
            sent = Main.sentCountMqtt.get();
        } else if (Config.mqttUdp.enabled) {
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

        saveToCsv(protocol, clients, overhead, intensity, sent, received, lossRatio, avgDelay, avgPacketSize);

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

    private static void saveToCsv(String protocol, int clients, int overhead, double intensity, long sent, long received,
            double lossRatio, Double delay, Double size) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%s,%d,%d,%.1f,%d,%d,%.6f,%.4f,%.4f%n",
                    protocol, clients, overhead, intensity, sent, received, lossRatio,
                    (delay != null ? delay : 0.0),
                    (size != null ? size : 0.0));
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private static void warmup() {
        System.out.printf("[WARMUP] Starting warm-up phase (%d seconds)...%n", WARMUP_DURATION_SECONDS);

        for (String protocol : new String[] { "MQTT", "MQTT-UDP" }) {
            Config.isRunning = true;
            Config.mqtt.enabled = protocol.equals("MQTT");
            Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
            Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
            Config.countClients = 1;
            Config.intensity = 20.0;
            Config.bytesOverhead = 4096; // 4KB payload in warmup too

            List<Thread> threads = new ArrayList<>();
            if (Config.mqtt.enabled) {
                String clientId = Config.mqtt.clientIdPrefix + "_LossWarmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(clientId)));
            } else if (Config.mqttUdp.enabled) {
                ru.dz.mqtt_udp.Engine.setThrottle(0);
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
            } else if (Config.mqttQuic.enabled) {
                String clientId = Config.mqttQuic.clientIdPrefix + "_LossWarmup";
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId)));
            }

            sleepSeconds(WARMUP_DURATION_SECONDS / 3);

            Config.isRunning = false;
            for (Thread t : threads) {
                try {
                    t.join(TimeUnit.SECONDS.toMillis(35));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        clearServerMetrics(Config.mqtt.metricsHost, Config.mqtt.metricsPort);
        clearServerMetrics(Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);
        resetServerCount(Config.mqtt.metricsHost, Config.mqtt.metricsPort);
        resetServerCount(Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);

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