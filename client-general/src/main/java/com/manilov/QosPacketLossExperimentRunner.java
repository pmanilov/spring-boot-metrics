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

public class QosPacketLossExperimentRunner {
    private static final int[] CLIENT_COUNTS = {1, 5, 10, 15};
    private static final int[] PAYLOAD_SIZES = {0, 2048, 4096, 8192};
    private static final int[] QOS_LEVELS = {0, 1, 2};
    private static final String[] PROTOCOLS = {"MQTT", "MQTT-QUIC"};
    private static final double INTENSITY_START = 100;
    private static final double INTENSITY_END = 1000;
    private static final double INTENSITY_STEP = 100;

    // Reduced timing budget for validation runs.
    private static final int TOTAL_PACKETS_PER_TEST = 20000;
    private static final int MIN_DURATION_SECONDS = 60;
    private static final int WARMUP_DURATION_SECONDS = 60;
    private static final int DRAIN_SECONDS = 60;
    private static final int POST_RUN_PAUSE_SECONDS = 30;
    private static final String CSV_FILE = "experiment_results_packet_loss_qos_all.csv";
    private static final String CSV_HEADER = "Protocol,QoS,Clients,Overhead(bytes),IntensityPerClient(req/sec),"
            + "TargetAggregateRate(req/sec),ActualSendRate(req/sec),ActualReceiveRate(req/sec),Sent,Received,"
            + "LossRatio,AvgDelay(ms),MedianDelay(ms),P95Delay(ms),P99Delay(ms),AvgPacketSize(bytes)";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        Set<String> completed = loadCompletedRuns();
        boolean resuming = !completed.isEmpty();

        if (!resuming) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
                writer.println(CSV_HEADER);
            } catch (IOException e) {
                System.err.println("Could not create CSV file: " + e.getMessage());
                return;
            }
            warmup();
        } else {
            ensureCurrentCsvHeader();
            System.out.printf("[RESUME] Found %d completed runs in %s, skipping warmup.%n",
                    completed.size(), CSV_FILE);
        }
        for (int clients : CLIENT_COUNTS) {
            for (int qos : QOS_LEVELS) {
                for (int overhead : PAYLOAD_SIZES) {
                    for (double intensity = INTENSITY_START; intensity <= INTENSITY_END; intensity += INTENSITY_STEP) {
                        int duration = Math.max(MIN_DURATION_SECONDS, (int) (TOTAL_PACKETS_PER_TEST / intensity));
                        Config.intensity = intensity;
                        Config.countClients = clients;

                         runIfNeeded(completed, qos, "MQTT", clients, overhead, intensity, duration,
                                 Config.mqtt.metricsHost, Config.mqtt.metricsPort);

                        runIfNeeded(completed, qos, "MQTT-QUIC", clients, overhead, intensity, duration,
                                Config.mqttQuic.metricsHost, Config.mqttQuic.metricsPort);
                    }
                }
            }
        }
    }

    private static String runKey(int qos, String protocol, int clients, int overhead, double intensity) {
        return String.format("%s|%d|%d|%d|%.1f", protocol, qos, clients, overhead, intensity);
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
                if (header) {
                    header = false;
                    continue;
                }
                if (line.isBlank()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 10) {
                    continue;
                }
                try {
                    boolean rateSchema = p.length >= 13;
                    long sent = Long.parseLong(rateSchema ? p[8] : p[5]);
                    long received = Long.parseLong(rateSchema ? p[9] : p[6]);
                    if (sent == 0 || received == 0) {
                        continue;
                    }
                    done.add(runKey(Integer.parseInt(p[1]), p[0], Integer.parseInt(p[2]),
                            Integer.parseInt(p[3]), Double.parseDouble(p[4])));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read existing CSV: " + e.getMessage());
        }
        return done;
    }

    private static void runIfNeeded(Set<String> completed, int qos, String protocol, int clients, int overhead,
                                    double intensity, int durationSeconds, String metricsHost, int metricsPort) {
        String key = runKey(qos, protocol, clients, overhead, intensity);
        if (completed.contains(key)) {
            System.out.printf("[%s QoS %d] Skip (already done): Clients=%d, Overhead=%dB, Int=%.1f%n",
                    protocol, qos, clients, overhead, intensity);
            return;
        }
        runProtocolTest(qos, protocol, clients, overhead, intensity, durationSeconds, metricsHost, metricsPort);
        completed.add(key);
    }

    private static void runProtocolTest(int qos, String protocol, int clients, int overhead, double intensity,
                                        int durationSeconds, String metricsHost, int metricsPort) {
        System.out.printf("[%s QoS %d] Starting... (Clients: %d, Overhead: %dB, Int: %.1f, Dur: %ds)%n",
                protocol, qos, clients, overhead, intensity, durationSeconds);

        clearServerMetrics(metricsHost, metricsPort);
        resetServerCount(metricsHost, metricsPort);
        Main.sentCountMqtt.set(0);
        Main.sentCountMqttUdp.set(0);
        Main.sentCountMqttQuic.set(0);
        ru.dz.mqtt_udp.Engine.resetQoSCounters();
        ru.dz.mqtt_udp.Engine.resetDedup();

        Config.isRunning = true;
        Config.mqtt.enabled = protocol.equals("MQTT");
        Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
        Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
        Config.mqtt.qos = qos;
        Config.mqttUdp.qos = qos;
        Config.mqttQuic.qos = qos;
        Config.countClients = clients;
        Config.bytesOverhead = overhead;

        List<Thread> threads = new ArrayList<>();

        if (Config.mqtt.enabled) {
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.mqtt.clientIdPrefix + "_QosLoss_" + qos + "_" + i;
                threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(clientId)));
            }
        } else if (Config.mqttUdp.enabled) {
            ru.dz.mqtt_udp.Engine.setThrottle(0);
            for (int i = 0; i < Config.countClients; i++) {
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
            }
        } else {
            // QUIC-подписчик мог потерять сессию между прогонами; QoS 0 на стороне
            // EMQX не буферизуется (MQUEUE_STORE_QOS0=false), так что без явного
            // ожидания готовности часть стартовых публикаций «съедается».
            // Принудительно роняем прошлую сессию, чтобы EMQX выкинул
            // накопленные inflight/awaiting_rel/mqueue — clean_start в CONNECT
            // даст чистый старт; иначе при clients>=10 state копится и валит подписчика.
            recycleSubscriber(metricsHost, metricsPort);
            awaitSubscriberReady(metricsHost, metricsPort);
            for (int i = 0; i < Config.countClients; i++) {
                String clientId = Config.mqttQuic.clientIdPrefix + "_QosLoss_" + qos + "_" + i;
                threads.add(Thread.ofVirtual().start(Main.getTaskMqttQuic(clientId)));
            }
        }

        sleepSeconds(durationSeconds);

        Config.isRunning = false;

        for (Thread t : threads) {
            try {
                t.join(TimeUnit.SECONDS.toMillis(35));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

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
        Double medianDelay = getMetric(metricsHost, metricsPort, "delay/median");
        Double p95Delay = getMetric(metricsHost, metricsPort, "delay/p95");
        Double p99Delay = getMetric(metricsHost, metricsPort, "delay/p99");
        Double avgPacketSize = getMetric(metricsHost, metricsPort, "packet-size/avg");
        double targetAggregateRate = intensity * clients;
        double actualSendRate = durationSeconds > 0 ? (double) sent / durationSeconds : 0.0;
        double actualReceiveRate = durationSeconds > 0 ? (double) received / durationSeconds : 0.0;

        System.out.printf("[%s QoS %d] Result: Sent=%d, Received=%d, Loss=%.4f, Delay=%.2f ms, "
                        + "Median=%.2f ms, P95=%.2f ms, P99=%.2f ms, Size=%.2f bytes, Out=%.2f msg/s%n",
                protocol, qos, sent, received, lossRatio, avgDelay, medianDelay, p95Delay, p99Delay,
                avgPacketSize, actualReceiveRate);

        saveToCsv(protocol, qos, clients, overhead, intensity, sent, received, lossRatio, avgDelay,
                medianDelay, p95Delay, p99Delay, avgPacketSize, targetAggregateRate, actualSendRate,
                actualReceiveRate);

        sleepSeconds(POST_RUN_PAUSE_SECONDS);
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

    private static void recycleSubscriber(String host, int port) {
        try {
            String url = "http://" + host + ":" + port + "/metrics/recycle";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                System.err.printf("[RECYCLE] %s returned %d%n", url, response.statusCode());
            }
        } catch (Exception e) {
            System.err.println("Failed to recycle subscriber: " + e.getMessage());
        }
    }

    private static void awaitSubscriberReady(String host, int port) {
        String url = "http://" + host + ":" + port + "/metrics/ready";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        int attempts = 0;
        while (System.nanoTime() < deadline) {
            attempts++;
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    if (attempts > 1) {
                        System.out.printf("[READY] Subscriber ready at %s after %d attempts%n", url, attempts);
                    }
                    return;
                }
            } catch (Exception ignored) {
                // эндпоинт может временно не отвечать — продолжаем
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.err.printf("[READY] Subscriber not ready at %s within 30s — proceeding anyway%n", url);
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

    private static void saveToCsv(String protocol, int qos, int clients, int overhead, double intensity, long sent,
                                  long received, double lossRatio, Double delay, Double medianDelay,
                                  Double p95Delay, Double p99Delay, Double size,
                                  double targetAggregateRate, double actualSendRate, double actualReceiveRate) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%s,%d,%d,%d,%.1f,%.4f,%.4f,%.4f,%d,%d,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                    protocol, qos, clients, overhead, intensity, targetAggregateRate, actualSendRate,
                    actualReceiveRate, sent, received, lossRatio,
                    delay != null ? delay : 0.0,
                    medianDelay != null ? medianDelay : 0.0,
                    p95Delay != null ? p95Delay : 0.0,
                    p99Delay != null ? p99Delay : 0.0,
                    size != null ? size : 0.0);
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private static void ensureCurrentCsvHeader() {
        File f = new File(CSV_FILE);
        if (!f.isFile()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            System.err.println("Could not read CSV header: " + e.getMessage());
            return;
        }

        if (lines.isEmpty() || CSV_HEADER.equals(lines.getFirst())) {
            return;
        }

        List<String> migrated = new ArrayList<>();
        migrated.add(CSV_HEADER);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.split(",", -1);
            if (p.length >= 16) {
                migrated.add(line);
            } else if (p.length >= 15) {
                migrated.add(migratePercentileCsvRow(p));
            } else if (p.length >= 13) {
                migrated.add(migrateRateCsvRow(p));
            } else if (p.length >= 10) {
                try {
                    migrated.add(migrateLegacyCsvRow(p));
                } catch (NumberFormatException e) {
                    System.err.printf("Skipping malformed CSV row %d during migration: %s%n", i + 1, e.getMessage());
                }
            }
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            for (String line : migrated) {
                writer.println(line);
            }
        } catch (IOException e) {
            System.err.println("Could not update CSV header: " + e.getMessage());
        }
    }

    private static String migrateLegacyCsvRow(String[] p) {
        int clients = Integer.parseInt(p[2]);
        double intensityPerClient = Double.parseDouble(p[4]);
        long sent = Long.parseLong(p[5]);
        long received = Long.parseLong(p[6]);
        int durationSeconds = Math.max(MIN_DURATION_SECONDS, (int) (TOTAL_PACKETS_PER_TEST / intensityPerClient));
        double targetAggregateRate = intensityPerClient * clients;
        double actualSendRate = durationSeconds > 0 ? (double) sent / durationSeconds : 0.0;
        double actualReceiveRate = durationSeconds > 0 ? (double) received / durationSeconds : 0.0;
        return String.format("%s,%s,%s,%s,%.1f,%.4f,%.4f,%.4f,%s,%s,%s,%s,0.0000,0.0000,0.0000,%s",
                p[0], p[1], p[2], p[3], intensityPerClient, targetAggregateRate, actualSendRate,
                actualReceiveRate, p[5], p[6], p[7], p[8], p[9]);
    }

    private static String migrateRateCsvRow(String[] p) {
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,0.0000,0.0000,0.0000,%s",
                p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], p[12]);
    }

    private static String migratePercentileCsvRow(String[] p) {
        return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,0.0000,%s,%s,%s",
                p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11],
                p[12], p[13], p[14]);
    }

    private static void warmup() {
        System.out.printf("[WARMUP] Starting warm-up phase (%d seconds)...%n", WARMUP_DURATION_SECONDS);

        int segmentDuration = Math.max(1, WARMUP_DURATION_SECONDS / (QOS_LEVELS.length * PROTOCOLS.length));
        for (int qos : QOS_LEVELS) {
            for (String protocol : PROTOCOLS) {
                Config.isRunning = true;
                Config.mqtt.enabled = protocol.equals("MQTT");
                Config.mqttUdp.enabled = protocol.equals("MQTT-UDP");
                Config.mqttQuic.enabled = protocol.equals("MQTT-QUIC");
                Config.mqtt.qos = qos;
                Config.mqttUdp.qos = qos;
                Config.mqttQuic.qos = qos;
                Config.countClients = 1;
                Config.intensity = 20.0;
                Config.bytesOverhead = 4096;

                List<Thread> threads = new ArrayList<>();
                if (Config.mqtt.enabled) {
                    threads.add(Thread.ofVirtual().start(Main.getTaskMQTT(
                            Config.mqtt.clientIdPrefix + "_QosWarmup_" + qos)));
                } else if (Config.mqttUdp.enabled) {
                    ru.dz.mqtt_udp.Engine.setThrottle(0);
                    threads.add(Thread.ofVirtual().start(Main.getTaskMqttUdp()));
                } else {
                    threads.add(Thread.ofVirtual().start(Main.getTaskMqttQuic(
                            Config.mqttQuic.clientIdPrefix + "_QosWarmup_" + qos)));
                }

                sleepSeconds(segmentDuration);

                Config.isRunning = false;
                for (Thread t : threads) {
                    try {
                        t.join(TimeUnit.SECONDS.toMillis(35));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        clearServerMetrics(Config.mqtt.metricsHost, Config.mqtt.metricsPort);
        clearServerMetrics(Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);
        clearServerMetrics(Config.mqttQuic.metricsHost, Config.mqttQuic.metricsPort);
        resetServerCount(Config.mqtt.metricsHost, Config.mqtt.metricsPort);
        resetServerCount(Config.mqttUdp.metricsHost, Config.mqttUdp.metricsPort);
        resetServerCount(Config.mqttQuic.metricsHost, Config.mqttQuic.metricsPort);

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
