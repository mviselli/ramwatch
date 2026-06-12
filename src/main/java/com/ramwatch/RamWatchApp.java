package com.ramwatch;

import com.ramwatch.ui.DashboardController;
import com.ramwatch.ui.DashboardView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class RamWatchApp extends Application {

    private DashboardController controller;

    @Override
    public void start(Stage stage) {
        DashboardView view = new DashboardView();
        controller = new DashboardController(view);

        Scene scene = new Scene(view, 720, 580);
        stage.setTitle("RamWatch");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> controller.stop());
        stage.show();

        controller.start(2);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
