package com.ramwatch;

import com.ramwatch.config.AppConfig;
import com.ramwatch.config.ConfigStore;
import com.ramwatch.ui.DashboardController;
import com.ramwatch.ui.DashboardView;
import com.ramwatch.ui.SettingsView;
import com.ramwatch.storage.EventCsvExporter;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
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

        view.setExportCallback(() -> exportCsv(stage));

        stage.setTitle("RamWatch");
        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(500);
        stage.setOnCloseRequest(e -> controller.stop());
        stage.show();

        controller.start();
    }

    /** Asks where to save, then converts the recorded events into a CSV file. */
    private void exportCsv(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export events as CSV");
        chooser.setInitialFileName("ramwatch-events.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));

        File target = chooser.showSaveDialog(stage);
        if (target == null) {
            return;
        }
        try {
            int count = EventCsvExporter.exportLog(
                    controller.currentConfig().logFilePath(), target.toPath());
            info(stage, count == 0
                    ? "No events recorded yet — the CSV contains only the header."
                    : count + (count == 1 ? " event exported to " : " events exported to ")
                            + target.getName());
        } catch (IOException e) {
            error(stage, "Could not write the CSV file:\n" + e.getMessage());
        }
    }

    private static void info(Stage owner, String message) {
        show(owner, Alert.AlertType.INFORMATION, message);
    }

    private static void error(Stage owner, String message) {
        show(owner, Alert.AlertType.ERROR, message);
    }

    private static void show(Stage owner, Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
