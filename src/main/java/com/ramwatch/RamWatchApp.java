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
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * JavaFX entry point: builds the window and wires the pieces together.
 *
 * <p>This class owns the composition, not the behaviour. It loads the stored configuration,
 * creates the view and its controller, registers what the toolbar buttons do — switch theme,
 * open the settings, export the events — and starts polling once the stage is on screen.
 *
 * <p>Launch it with {@link #main(String[])}, or through {@code mvn javafx:run}.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public class RamWatchApp extends Application {

    /** Drives the dashboard; created in {@link #start} and stopped when the window closes. */
    private DashboardController controller;

    /** Persists the user's preferences at the default location. */
    private ConfigStore configStore;

    /** The name the app goes by, in the window title and in the desktop's own furniture. */
    private static final String APP_NAME = "RamWatch";

    /** The app icon, as a classpath resource. */
    private static final String ICON_RESOURCE = "/com/ramwatch/icon.png";

    /** Instantiated by the JavaFX runtime, which then calls {@link #start(Stage)}. */
    public RamWatchApp() {}

    /**
     * Builds the window and starts monitoring.
     *
     * <p>Called by the JavaFX runtime on the application thread. Themes are two stylesheets
     * swapped on the scene, and closing the window stops the polling thread so the JVM can
     * exit cleanly.
     *
     * @param stage the primary stage supplied by the JavaFX runtime
     */
    @Override
    public void start(Stage stage) {
        configStore = ConfigStore.atDefaultLocation();
        AppConfig initialConfig = configStore.load();

        DashboardView view = new DashboardView();
        controller = new DashboardController(view, initialConfig);

        Scene scene = new Scene(view, 860, 760);

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

        stage.setTitle(APP_NAME);
        applyIcon(stage);
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(620);
        stage.setOnCloseRequest(e -> controller.stop());
        stage.show();

        controller.start();
    }

    /**
     * Gives the window its icon, for the platforms that read it off the stage.
     *
     * <p>macOS is not one of them: it shows what the launcher gave the process, which
     * {@link #brand()} deals with before the window exists.
     *
     * @param stage the primary stage
     */
    private void applyIcon(Stage stage) {
        URL icon = getClass().getResource(ICON_RESOURCE);
        if (icon != null) {
            stage.getIcons().add(new Image(icon.toExternalForm()));
        }
    }

    /**
     * Asks where to save, then converts the recorded events into a CSV file.
     *
     * <p>The outcome is always reported: how many events were written, that there were none
     * to write, or why the file could not be created. Cancelling the chooser does nothing.
     *
     * @param stage the window the file chooser and the dialogs belong to
     */
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

    /**
     * Shows an informational dialog.
     *
     * @param owner   the window the dialog belongs to
     * @param message the text to show
     */
    private static void info(Stage owner, String message) {
        show(owner, Alert.AlertType.INFORMATION, message);
    }

    /**
     * Shows an error dialog.
     *
     * @param owner   the window the dialog belongs to
     * @param message the text to show
     */
    private static void error(Stage owner, String message) {
        show(owner, Alert.AlertType.ERROR, message);
    }

    /**
     * Shows a modal dialog styled like the rest of the app and waits for it to close.
     *
     * <p>The owner's stylesheets are copied onto the dialog, otherwise it would ignore the
     * current theme.
     *
     * @param owner   the window the dialog belongs to
     * @param type    the kind of dialog to show
     * @param message the text to show
     */
    private static void show(Stage owner, Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.initOwner(owner);
        alert.setHeaderText(null);
        alert.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        alert.showAndWait();
    }

    /**
     * Starts the application.
     *
     * @param args command-line arguments, passed through to the JavaFX runtime
     */
    public static void main(String[] args) {
        brand();
        launch(args);
    }

    /**
     * Gives the process a name and a dock icon of its own, before any window exists.
     *
     * <p>Launched as a plain JVM, the app introduces itself to macOS as "java" and gets the
     * generic icon that goes with it. The name and the icon are read once, when the desktop
     * first hears about the process, so both have to be set before JavaFX starts — which is
     * what this method is for and why it runs from {@code main} rather than {@code start}.
     *
     * <p>The steps are all best-effort: on a platform with no dock, or one that will not let a
     * process rename itself, the app simply keeps the name it was given.
     */
    private static void brand() {
        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", APP_NAME);
        try {
            URL icon = RamWatchApp.class.getResource(ICON_RESOURCE);
            if (icon != null && Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(ImageIO.read(icon));
                }
            }
        } catch (Exception ignored) {
            // No dock to put an icon on, or the platform will not let us change it.
        }
    }
}
