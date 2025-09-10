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
        Label mqttLabel = new Label("MQTT Configuration");
        mqttLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane mqttGrid = new GridPane();
        mqttGrid.setVgap(8);
        mqttGrid.setHgap(10);

        TextField mqttHostField = new TextField(Config.hostname);
        TextField mqttPortField = new TextField(String.valueOf(Config.mqttPort));
        TextField mqttTopicField = new TextField(Config.topicMqtt);
        TextField mqttClientIdField = new TextField(Config.clientIdPrefixMqtt);

        mqttGrid.add(new Label("Broker Host:"), 0, 0);
        mqttGrid.add(mqttHostField, 1, 0);
        mqttGrid.add(new Label("Broker Port:"), 0, 1);
        mqttGrid.add(mqttPortField, 1, 1);
        mqttGrid.add(new Label("Topic:"), 0, 2);
        mqttGrid.add(mqttTopicField, 1, 2);
        mqttGrid.add(new Label("Client ID Prefix:"), 0, 3);
        mqttGrid.add(mqttClientIdField, 1, 3);

        mqttBox.getChildren().addAll(mqttLabel, mqttGrid);

        VBox mqttUdpBox = new VBox(10);
        mqttUdpBox.setStyle("-fx-border-color: #cccccc; -fx-border-width: 1; -fx-padding: 10;");
        Label mqttUdpLabel = new Label("MQTT-UDP Configuration");
        mqttUdpLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14;");

        GridPane mqttUdpGrid = new GridPane();
        mqttUdpGrid.setVgap(8);
        mqttUdpGrid.setHgap(10);

        TextField mqttUdpTopicField = new TextField(Config.topicMqttUdp);

        mqttUdpGrid.add(new Label("Topic:"), 0, 0);
        mqttUdpGrid.add(mqttUdpTopicField, 1, 0);

        mqttUdpBox.getChildren().addAll(mqttUdpLabel, mqttUdpGrid);

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

        TextField periodField = new TextField(String.valueOf(Config.period));
        TextField countField = new TextField(String.valueOf(Config.countClients));

        commonGrid.add(new Label("Period (ms):"), 0, 0);
        commonGrid.add(periodField, 1, 0);
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

        TextField metricsHostMqttField = new TextField(Config.metricsHostMqtt);
        TextField metricsPortMqttField = new TextField(String.valueOf(Config.metricsPortMqtt));
        TextField metricsHostMqttUdpField = new TextField(Config.metricsHostMqttUdp);
        TextField metricsPortMqttUdpField = new TextField(String.valueOf(Config.metricsPortMqttUdp));

        metricsGrid.add(new Label("Server MQTT Host:"), 0, 0);
        metricsGrid.add(metricsHostMqttField, 1, 0);
        metricsGrid.add(new Label("Server MQTT Port:"), 0, 1);
        metricsGrid.add(metricsPortMqttField, 1, 1);
        metricsGrid.add(new Label("Server MQTT-UDP Host:"), 0, 2);
        metricsGrid.add(metricsHostMqttUdpField, 1, 2);
        metricsGrid.add(new Label("Server MQTT-UDP Port:"), 0, 3);
        metricsGrid.add(metricsPortMqttUdpField, 1, 3);

        metricsBox.getChildren().addAll(metricsLabel, metricsGrid);

        Button clearMetricsBtn = new Button("Clear");
        clearMetricsBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20; " +
                "-fx-border-width: 1; -fx-border-color: #555555;");
        clearMetricsBtn.setOnAction(e -> clearMetrics());

        Button startBtn = new Button("Start");
        startBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20; " +
                "-fx-background-color: #50b355; -fx-text-fill: white; " +
                "-fx-border-width: 1; -fx-border-color: #555555;");

        Button stopBtn = new Button("Stop");
        stopBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20; " +
                "-fx-background-color: #dc443a; -fx-text-fill: white; " +
                "-fx-border-width: 1; -fx-border-color: #555555;");
        stopBtn.setDisable(true);

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().addAll(clearMetricsBtn, startBtn, stopBtn);

        VBox bottomBox = new VBox(10);
        bottomBox.getChildren().addAll(commonBox, metricsBox, buttonBox);
        mainPane.setBottom(bottomBox);

        startBtn.setOnAction(e -> {
            try {
                Config.hostname = mqttHostField.getText();
                Config.mqttPort = Integer.parseInt(mqttPortField.getText());
                Config.topicMqtt = mqttTopicField.getText();
                Config.clientIdPrefixMqtt = mqttClientIdField.getText();
                Config.topicMqttUdp = mqttUdpTopicField.getText();
                Config.period = Long.parseLong(periodField.getText());
                Config.countClients = Integer.parseInt(countField.getText());

                Config.metricsHostMqtt = metricsHostMqttField.getText();
                Config.metricsPortMqtt = Integer.parseInt(metricsPortMqttField.getText());
                Config.metricsHostMqttUdp = metricsHostMqttUdpField.getText();
                Config.metricsPortMqttUdp = Integer.parseInt(metricsPortMqttUdpField.getText());

                if (!Config.started) {
                    Config.started = true;
                    Config.paused = false;
                    new Thread(() -> Main.main(new String[]{})).start();
                    System.out.println("Publishing started");
                } else {
                    Config.paused = false;
                    System.out.println("Publishing resumed");
                }
                startBtn.setDisable(true);
                stopBtn.setDisable(false);

            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Ошибка ввода: " + ex.getMessage()).showAndWait();
            }
        });

        stopBtn.setOnAction(e -> {
            if (Config.started) {
                Config.paused = true;
                System.out.println("Publishing paused");
            }
            stopBtn.setDisable(true);
            startBtn.setDisable(false);
        });


        Scene scene = new Scene(mainPane, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void clearMetrics() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            String url1 = "http://" + Config.metricsHostMqtt + ":" + Config.metricsPortMqtt + "/metrics/delete";
            HttpRequest request1 = HttpRequest.newBuilder()
                    .uri(URI.create(url1))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            String url2 = "http://" + Config.metricsHostMqttUdp + ":" + Config.metricsPortMqttUdp + "/metrics/delete";
            HttpRequest request2 = HttpRequest.newBuilder()
                    .uri(URI.create(url2))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            client.sendAsync(request1, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Metrics cleared on server 1: " + Config.metricsHostMqtt + ":" + Config.metricsPortMqtt);
                        } else {
                            System.out.println("Failed to clear metrics on server 1: " + response.statusCode());
                        }
                    });

            client.sendAsync(request2, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Metrics cleared on server 2: " + Config.metricsHostMqttUdp + ":" + Config.metricsPortMqttUdp);
                        } else {
                            System.out.println("Failed to clear metrics on server 2: " + response.statusCode());
                        }
                    });

            new Alert(Alert.AlertType.INFORMATION, "Requests is send:\n" +
                    Config.metricsHostMqtt + ":" + Config.metricsPortMqtt + "\n" +
                    Config.metricsHostMqttUdp + ":" + Config.metricsPortMqttUdp).showAndWait();

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Error while deleting metrics: " + ex.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}