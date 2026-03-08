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

public class ExperimentRunner {
    private static final int[] LOSS_PERCENTAGES = { 0, 10, 20, 30, 50, 70, 80 };
    private static final int[] CLIENT_COUNTS = { 1 };
    private static final double[] INTENSITIES = { 1, 5, 10, 20, 30, 40, 50 };

    private static final int TEST_DURATION_SECONDS = 300;
    private static final int WARMUP_DURATION_SECONDS = 600;
    private static final String CSV_FILE = "experiment_results.csv";

    private static final String SCRIPT_SET_LOSS = "./set_loss.sh";
    private static final String SCRIPT_RESET_LOSS = "./reset_loss.sh";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Protocol,PacketLoss(%),Clients,Intensity(req/sec),AvgDelay(ms),AvgPacketSize(bytes)");
        } catch (IOException e) {
            System.err.println("Could not create CSV file: " + e.getMessage());
            return;
        }

        warmup();

        for (int loss : LOSS_PERCENTAGES) {
            if (loss == 0) {
                executeBashScript(SCRIPT_RESET_LOSS);
            } else {
                if (!executeBashScript(SCRIPT_SET_LOSS, String.valueOf(loss))) {
                    System.err.println("Failed to set network loss. Aborting.");
                    return;
                }
            }
            for (int clients : CLIENT_COUNTS) {
                for (double intensity : INTENSITIES) {
                    Config.intensity = intensity;
                    Config.countClients = clients;

                    runProtocolTest("MQTT", loss, clients, intensity,
                            Config.metricsHostMqtt, Config.metricsPortMqtt);

                    sleepSeconds(5);

                    runProtocolTest("MQTT-UDP", loss, clients, intensity,
                            Config.metricsHostMqttUdp, Config.metricsPortMqttUdp);
                }
            }
        }

        executeBashScript(SCRIPT_RESET_LOSS);
    }

    private static void runProtocolTest(String protocol, int loss, int clients, double intensity,
            String metricsHost, int metricsPort) {

        System.out.printf("[%s] Starting... (Loss: %d%%, Clients: %d, Int: %.1f)\n",
                protocol, loss, clients, intensity);

        clearServerMetrics(metricsHost, metricsPort);

        Config.isRunning = true;
        Config.enableMqtt = protocol.equals("MQTT");
        Config.enableMqttUdp = protocol.equals("MQTT-UDP");
        Config.countClients = clients;

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

        saveToCsv(protocol, loss, clients, intensity, avgDelay, avgPacketSize);
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

    private static void saveToCsv(String protocol, int loss, int clients, double intensity, Double delay, Double size) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE, true))) {
            writer.printf("%s,%d,%d,%.1f,%.4f,%.4f%n",
                    protocol, loss, clients, intensity,
                    (delay != null ? delay : 0.0),
                    (size != null ? size : 0.0));
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }

    private static boolean executeBashScript(String scriptPath, String... args) {
        try {
            List<String> command = new ArrayList<>();
            command.add("sudo");
            command.add(scriptPath);
            if (args != null) {
                for (String arg : args) {
                    command.add(arg);
                }
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            System.err.println("Script execution failed: " + e.getMessage());
            return false;
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