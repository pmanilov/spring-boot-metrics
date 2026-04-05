package com.manilov;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ConfigUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("MQTT и MQTT-UDP Configurator");

        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(15));

        HBox contentBox = new HBox(20);
        contentBox.setPadding(new Insets(0, 0, 15, 0));

        VBox mqttBox = new VBox(10);
        mqttBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");

        CheckBox mqttEnableCb = new CheckBox("Enable MQTT");
        mqttEnableCb.setSelected(Config.mqtt.enabled);
        mqttEnableCb.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane mqttGrid = new GridPane();
        mqttGrid.setVgap(8);
        mqttGrid.setHgap(10);

        TextField mqttHostField = new TextField(Config.mqtt.brokerHost);
        TextField mqttPortField = new TextField(String.valueOf(Config.mqtt.brokerPort));
        TextField mqttTopicField = new TextField(Config.mqtt.topic);
        TextField mqttClientIdField = new TextField(Config.mqtt.clientIdPrefix);

        mqttGrid.add(new Label("Broker Host:"), 0, 0);
        mqttGrid.add(mqttHostField, 1, 0);
        mqttGrid.add(new Label("Broker Port:"), 0, 1);
        mqttGrid.add(mqttPortField, 1, 1);
        mqttGrid.add(new Label("Topic:"), 0, 2);
        mqttGrid.add(mqttTopicField, 1, 2);
        mqttGrid.add(new Label("Client ID Prefix:"), 0, 3);
        mqttGrid.add(mqttClientIdField, 1, 3);

        mqttBox.getChildren().addAll(mqttEnableCb, mqttGrid);

        VBox mqttUdpBox = new VBox(10);
        mqttUdpBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");

        CheckBox mqttUdpEnableCb = new CheckBox("Enable MQTT-UDP");
        mqttUdpEnableCb.setSelected(Config.mqttUdp.enabled);
        mqttUdpEnableCb.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane mqttUdpGrid = new GridPane();
        mqttUdpGrid.setVgap(8);
        mqttUdpGrid.setHgap(10);

        TextField mqttUdpTopicField = new TextField(Config.mqttUdp.topic);

        mqttUdpGrid.add(new Label("Topic:"), 0, 0);
        mqttUdpGrid.add(mqttUdpTopicField, 1, 0);

        mqttUdpBox.getChildren().addAll(mqttUdpEnableCb, mqttUdpGrid);

        HBox.setHgrow(mqttBox, Priority.ALWAYS);
        HBox.setHgrow(mqttUdpBox, Priority.ALWAYS);
        mqttBox.setMaxWidth(Double.MAX_VALUE);
        mqttUdpBox.setMaxWidth(Double.MAX_VALUE);

        contentBox.getChildren().addAll(mqttBox, mqttUdpBox);
        mainPane.setCenter(contentBox);

        VBox commonBox = new VBox(10);
        commonBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");
        Label commonLabel = new Label("Common Configuration");
        commonLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane commonGrid = new GridPane();
        commonGrid.setVgap(8);
        commonGrid.setHgap(10);

        TextField intensityField = new TextField(String.valueOf(Config.intensity));
        TextField countField = new TextField(String.valueOf(Config.countClients));

        commonGrid.add(new Label("Intensity (req/sec):"), 0, 0);
        commonGrid.add(intensityField, 1, 0);
        commonGrid.add(new Label("Clients Count:"), 0, 1);
        commonGrid.add(countField, 1, 1);

        commonBox.getChildren().addAll(commonLabel, commonGrid);

        VBox metricsBox = new VBox(10);
        metricsBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");
        Label metricsLabel = new Label("Metrics Servers Configuration");
        metricsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane metricsGrid = new GridPane();
        metricsGrid.setVgap(8);
        metricsGrid.setHgap(10);

        TextField metricsHostMqttField = new TextField(Config.mqtt.metricsHost);
        TextField metricsPortMqttField = new TextField(String.valueOf(Config.mqtt.metricsPort));
        TextField metricsHostMqttUdpField = new TextField(Config.mqttUdp.metricsHost);
        TextField metricsPortMqttUdpField = new TextField(String.valueOf(Config.mqttUdp.metricsPort));

        metricsGrid.add(new Label("Server MQTT Host:"), 0, 0);
        metricsGrid.add(metricsHostMqttField, 1, 0);
        metricsGrid.add(new Label("Server MQTT Port:"), 0, 1);
        metricsGrid.add(metricsPortMqttField, 1, 1);
        metricsGrid.add(new Label("Server MQTT-UDP Host:"), 0, 2);
        metricsGrid.add(metricsHostMqttUdpField, 1, 2);
        metricsGrid.add(new Label("Server MQTT-UDP Port:"), 0, 3);
        metricsGrid.add(metricsPortMqttUdpField, 1, 3);

        metricsBox.getChildren().addAll(metricsLabel, metricsGrid);

        Button clearMetricsBtn = new Button("Clear Metrics");
        clearMetricsBtn.setOnAction(e -> clearMetrics());

        Button startBtn = new Button("Start");
        startBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20; -fx-background-color: #50b355; -fx-text-fill: white;");

        Button stopBtn = new Button("Stop");
        stopBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20; -fx-background-color: #dc443a; -fx-text-fill: white;");
        stopBtn.setDisable(true);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().addAll(clearMetricsBtn, startBtn, stopBtn);

        VBox bottomBox = new VBox(10);
        bottomBox.getChildren().addAll(commonBox, metricsBox, buttonBox);
        mainPane.setBottom(bottomBox);

        startBtn.setOnAction(e -> {
            try {
                Config.mqtt.enabled = mqttEnableCb.isSelected();
                Config.mqttUdp.enabled = mqttUdpEnableCb.isSelected();

                Config.mqtt.brokerHost = mqttHostField.getText();
                Config.mqtt.brokerPort = Integer.parseInt(mqttPortField.getText());
                Config.mqtt.topic = mqttTopicField.getText();
                Config.mqtt.clientIdPrefix = mqttClientIdField.getText();

                Config.mqttUdp.topic = mqttUdpTopicField.getText();

                Config.intensity = Double.parseDouble(intensityField.getText());
                Config.countClients = Integer.parseInt(countField.getText());

                Config.mqtt.metricsHost = metricsHostMqttField.getText();
                Config.mqtt.metricsPort = Integer.parseInt(metricsPortMqttField.getText());
                Config.mqttUdp.metricsHost = metricsHostMqttUdpField.getText();
                Config.mqttUdp.metricsPort = Integer.parseInt(metricsPortMqttUdpField.getText());

                Config.isRunning = true;


                new Thread(() -> Main.main(new String[]{})).start();
                System.out.println("Started. Intensity: " + Config.intensity);

                startBtn.setDisable(true);
                stopBtn.setDisable(false);

            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Ошибка ввода: " + ex.getMessage()).showAndWait();
            }
        });

        stopBtn.setOnAction(e -> {
            Config.isRunning = false;
            System.out.println("Stopped.");
            stopBtn.setDisable(true);
            startBtn.setDisable(false);
        });

        Scene scene = new Scene(mainPane, 800, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void clearMetrics() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String url1 = "http://" + Config.mqtt.metricsHost + ":" + Config.mqtt.metricsPort + "/metrics/delete";
            HttpRequest request1 = HttpRequest.newBuilder()
                    .uri(URI.create(url1))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            String url2 = "http://" + Config.mqttUdp.metricsHost + ":" + Config.mqttUdp.metricsPort + "/metrics/delete";
            HttpRequest request2 = HttpRequest.newBuilder()
                    .uri(URI.create(url2))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            client.sendAsync(request1, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Metrics cleared on server 1: " + Config.mqtt.metricsHost + ":" + Config.mqtt.metricsPort);
                        } else {
                            System.out.println("Failed to clear metrics on server 1: " + response.statusCode());
                        }
                    });

            client.sendAsync(request2, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Metrics cleared on server 2: " + Config.mqttUdp.metricsHost + ":" + Config.mqttUdp.metricsPort);
                        } else {
                            System.out.println("Failed to clear metrics on server 2: " + response.statusCode());
                        }
                    });

            new Alert(Alert.AlertType.INFORMATION, "Requests is send:\n" +
                    Config.mqtt.metricsHost + ":" + Config.mqtt.metricsPort + "\n" +
                    Config.mqttUdp.metricsHost + ":" + Config.mqttUdp.metricsPort).showAndWait();

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error while deleting metrics: " + ex.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}