package com.ramwatch;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class RamWatchApp extends Application {

    @Override
    public void start(Stage stage) {
        Label title = new Label("RamWatch");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Lightweight RAM monitoring utility");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(24));
        root.setTop(title);
        root.setCenter(subtitle);

        Scene scene = new Scene(root, 720, 480);
        stage.setTitle("RamWatch");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(420);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
