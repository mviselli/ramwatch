package com.ramwatch.ui;

import com.ramwatch.config.AppConfig;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Modal dialog that lets the user edit {@link AppConfig} fields.
 * Call {@link #show(AppConfig, Window, ObservableList, Consumer)} to open it.
 */
public final class SettingsView extends Dialog<AppConfig> {

    private final Spinner<Integer> spnPolling       = new Spinner<>(1, 60, 2);
    private final Spinner<Double>  spnWarning       = new Spinner<>(1.0, 99.0, 20.0, 1.0);
    private final Spinner<Double>  spnCritical      = new Spinner<>(1.0, 99.0, 10.0, 1.0);
    private final Spinner<Integer> spnMinMemoryMb   = new Spinner<>(0, 4096, 100);
    private final CheckBox         chkLogging       = new CheckBox();
    private final TextField        txtLogFile       = new TextField();
    private final Label            lblError         = new Label();

    private SettingsView(AppConfig current, Window owner, ObservableList<String> stylesheets) {
        initOwner(owner);
        setTitle("Settings");
        setHeaderText(null);
        setResizable(false);

        getDialogPane().getStylesheets().setAll(stylesheets);
        populate(current);

        getDialogPane().setContent(buildContent());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            String err = validate();
            if (err != null) {
                lblError.setText(err);
                e.consume();
            }
        });

        setResultConverter(btn -> {
            if (btn == ButtonType.OK) return buildConfig();
            return null;
        });
    }

    public static void show(AppConfig current, Window owner, ObservableList<String> stylesheets, Consumer<AppConfig> onSave) {
        new SettingsView(current, owner, stylesheets).showAndWait().ifPresent(onSave);
    }

    // ── layout ───────────────────────────────────────────────

    private GridPane buildContent() {
        spnPolling.setEditable(true);
        spnWarning.setEditable(true);
        spnCritical.setEditable(true);
        spnMinMemoryMb.setEditable(true);

        lblError.setStyle("-fx-text-fill: #ff3b30; -fx-font-size: 11px;");

        txtLogFile.setPrefColumnCount(24);
        // The log file only matters while logging is on.
        txtLogFile.disableProperty().bind(chkLogging.selectedProperty().not());

        Label lblLogging = new Label("Enable logging");
        lblLogging.setStyle("-fx-font-size: 13px;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 24, 8, 24));
        grid.setAlignment(Pos.TOP_LEFT);

        int row = 0;
        addRow(grid, row++, "Polling interval (s)", spnPolling);
        addRow(grid, row++, "Warning threshold — free RAM %", spnWarning);
        addRow(grid, row++, "Critical threshold — free RAM %", spnCritical);
        addRow(grid, row++, "Min process memory (MB)", spnMinMemoryMb);

        HBox loggingRow = new HBox(8, chkLogging, lblLogging);
        loggingRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(loggingRow, 0, row++, 2, 1);
        addRow(grid, row++, "Log file", txtLogFile);
        grid.add(lblError, 0, row, 2, 1);

        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, Control control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13px;");
        GridPane.setHgrow(control, Priority.ALWAYS);
        grid.add(lbl, 0, row);
        grid.add(control, 1, row);
    }

    // ── populate / build ─────────────────────────────────────

    private void populate(AppConfig cfg) {
        spnPolling.getValueFactory().setValue(cfg.pollingIntervalSeconds());
        spnWarning.getValueFactory().setValue(cfg.warningFreePercent());
        spnCritical.getValueFactory().setValue(cfg.criticalFreePercent());
        spnMinMemoryMb.getValueFactory().setValue((int) (cfg.minProcessMemoryBytes() / (1024L * 1024)));
        chkLogging.setSelected(cfg.loggingEnabled());
        txtLogFile.setText(cfg.logFilePath().toString());
    }

    private String validate() {
        commitSpinners();
        double warning  = spnWarning.getValue();
        double critical = spnCritical.getValue();
        if (critical >= warning) {
            return "Critical threshold must be lower than warning threshold.";
        }
        if (chkLogging.isSelected() && parseLogFile() == null) {
            return "Log file must be a valid path to a file.";
        }
        return null;
    }

    private AppConfig buildConfig() {
        commitSpinners();
        return AppConfig.builder()
                .pollingIntervalSeconds(spnPolling.getValue())
                .warningFreePercent(spnWarning.getValue())
                .criticalFreePercent(spnCritical.getValue())
                .minProcessMemoryBytes((long) spnMinMemoryMb.getValue() * 1024 * 1024)
                .loggingEnabled(chkLogging.isSelected())
                .logFilePath(logFilePathOrDefault())
                .build();
    }

    /** The typed log file, or {@code null} when it is blank or not a usable file path. */
    private Path parseLogFile() {
        String raw = txtLogFile.getText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(raw.strip());
            return path.getFileName() == null ? null : path;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private Path logFilePathOrDefault() {
        Path path = parseLogFile();
        return path != null ? path : AppConfig.defaultLogFilePath();
    }

    private void commitSpinners() {
        spnPolling.commitValue();
        spnWarning.commitValue();
        spnCritical.commitValue();
        spnMinMemoryMb.commitValue();
    }
}
