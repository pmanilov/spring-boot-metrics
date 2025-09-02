package com.manilov;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

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

        mqttGrid.add(new Label("Hostname:"), 0, 0);
        mqttGrid.add(mqttHostField, 1, 0);
        mqttGrid.add(new Label("Port:"), 0, 1);
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

        Button startBtn = new Button("Start");
        startBtn.setStyle("-fx-font-size: 14; -fx-padding: 8 20;");
        HBox buttonBox = new HBox();
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER);
        buttonBox.getChildren().add(startBtn);

        VBox bottomBox = new VBox(10);
        bottomBox.getChildren().addAll(commonBox, buttonBox);
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
                primaryStage.close();
                Main.main(new String[]{});
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Ошибка ввода: " + ex.getMessage()).showAndWait();
            }
        });

        Scene scene = new Scene(mainPane, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}