package com.ramwatch;

import com.ramwatch.config.AppConfig;
import com.ramwatch.config.ConfigStore;
import com.ramwatch.ui.DashboardController;
import com.ramwatch.ui.DashboardView;
import com.ramwatch.ui.SettingsView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class RamWatchApp extends Application {

    private DashboardController controller;
    private ConfigStore configStore;

    @Override
    public void start(Stage stage) {
        configStore = ConfigStore.atDefaultLocation();
        AppConfig initialConfig = configStore.load();

        DashboardView view = new DashboardView();
        controller = new DashboardController(view, initialConfig);

        Scene scene = new Scene(view, 720, 600);

        String lightCss = getClass().getResource("/com/ramwatch/ui/light.css").toExternalForm();
        String darkCss  = getClass().getResource("/com/ramwatch/ui/dark.css").toExternalForm();
        scene.getStylesheets().add(lightCss);

        view.setThemeToggleCallback(() -> {
            scene.getStylesheets().clear();
            if (view.isDarkMode()) {
                view.setDarkMode(false);
                scene.getStylesheets().add(lightCss);
            } else {
                view.setDarkMode(true);
                scene.getStylesheets().add(darkCss);
            }
        });

        view.setSettingsCallback(() ->
                SettingsView.show(controller.currentConfig(), stage, scene.getStylesheets(), newConfig -> {
                    controller.applyConfig(newConfig);
                    try { configStore.save(newConfig); } catch (IOException ignored) {}
                })
        );

        stage.setTitle("RamWatch");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(500);
        stage.setOnCloseRequest(e -> controller.stop());
        stage.show();

        controller.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
