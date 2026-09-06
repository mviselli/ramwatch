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
 *
 * <p>The dialog validates before closing: an OK that would produce an invalid configuration
 * is consumed and the reason shown inline, so the user never loses what they typed. The
 * relation between the two thresholds is checked here as well as in {@link AppConfig},
 * because a validation exception is no way to tell a user what to fix.
 *
 * <p>It edits a copy and returns a new configuration: nothing is applied or saved here. What
 * to do with the result is the caller's decision.
 *
 * <p>Call {@link #show(AppConfig, Window, ObservableList, Consumer)} to open it.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class SettingsView extends Dialog<AppConfig> {

    /** Polling interval in seconds; the range already enforces the one-second minimum. */
    private final Spinner<Integer> spnPolling       = new Spinner<>(1, 60, 2);

    /** Warning threshold as a percentage of free RAM. */
    private final Spinner<Double>  spnWarning       = new Spinner<>(1.0, 99.0, 20.0, 1.0);

    /** Critical threshold as a percentage of free RAM; must stay below the warning one. */
    private final Spinner<Double>  spnCritical      = new Spinner<>(1.0, 99.0, 10.0, 1.0);

    /** Process filter in megabytes; {@code 0} shows every process. */
    private final Spinner<Integer> spnMinMemoryMb   = new Spinner<>(0, 4096, 100);

    /** Whether events are written to the log file. */
    private final CheckBox         chkLogging       = new CheckBox();

    /** Log file path; disabled while logging is off. */
    private final TextField        txtLogFile       = new TextField();

    /** Inline message explaining why OK was refused; empty while the input is valid. */
    private final Label            lblError         = new Label();

    /**
     * Builds the dialog, filled with the configuration currently in force.
     *
     * <p>Private on purpose: {@link #show} is the only way in, so a caller cannot forget to
     * wait for the result.
     *
     * @param current     the configuration to edit; must not be {@code null}
     * @param owner       the window the dialog is modal to
     * @param stylesheets the scene's stylesheets, so the dialog follows the current theme
     */
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

    /**
     * Opens the dialog and blocks until the user closes it.
     *
     * <p>{@code onSave} runs only on OK with valid input; cancelling does nothing at all.
     * Call this on the JavaFX application thread.
     *
     * @param current     the configuration to edit; must not be {@code null}
     * @param owner       the window the dialog is modal to
     * @param stylesheets the scene's stylesheets, so the dialog follows the current theme
     * @param onSave      receives the new configuration when the user confirms; must not be
     *                    {@code null}
     */
    public static void show(AppConfig current, Window owner, ObservableList<String> stylesheets, Consumer<AppConfig> onSave) {
        new SettingsView(current, owner, stylesheets).showAndWait().ifPresent(onSave);
    }

    // ── layout ───────────────────────────────────────────────

    /**
     * Builds the form: one row per setting, with the error label underneath.
     *
     * @return the assembled grid
     */
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

    /**
     * Adds a labelled row to the form.
     *
     * @param grid    the grid to add to
     * @param row     zero-based row index
     * @param label   text shown in the first column
     * @param control editor placed in the second column
     */
    private static void addRow(GridPane grid, int row, String label, Control control) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 13px;");
        GridPane.setHgrow(control, Priority.ALWAYS);
        grid.add(lbl, 0, row);
        grid.add(control, 1, row);
    }

    // ── populate / build ─────────────────────────────────────

    /**
     * Fills the editors with the values of an existing configuration.
     *
     * @param cfg the configuration to display; must not be {@code null}
     */
    private void populate(AppConfig cfg) {
        spnPolling.getValueFactory().setValue(cfg.pollingIntervalSeconds());
        spnWarning.getValueFactory().setValue(cfg.warningFreePercent());
        spnCritical.getValueFactory().setValue(cfg.criticalFreePercent());
        spnMinMemoryMb.getValueFactory().setValue((int) (cfg.minProcessMemoryBytes() / (1024L * 1024)));
        chkLogging.setSelected(cfg.loggingEnabled());
        txtLogFile.setText(cfg.logFilePath().toString());
    }

    /**
     * Checks what the range of each editor cannot check on its own.
     *
     * <p>The log file is only validated while logging is on: an unusable path costs nothing
     * as long as nothing is written to it.
     *
     * @return the message to show, or {@code null} when the input is valid
     */
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

    /**
     * Builds the configuration the dialog returns.
     *
     * <p>Only called after {@link #validate()} passed, so the record's own checks cannot fail
     * here.
     *
     * @return the configuration described by the form
     */
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

    /**
     * The typed log file, or {@code null} when it is blank or not a usable file path.
     *
     * @return the parsed path, or {@code null} when the text cannot name a file
     */
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

    /**
     * The typed log file, falling back to the default location.
     *
     * <p>Reached with an unusable path only while logging is off, where the value is stored
     * but never written to.
     *
     * @return the typed path, or {@link AppConfig#defaultLogFilePath()} when it cannot be used
     */
    private Path logFilePathOrDefault() {
        Path path = parseLogFile();
        return path != null ? path : AppConfig.defaultLogFilePath();
    }

    /**
     * Commits what is typed in the editable spinners.
     *
     * <p>A spinner only publishes its value when the field loses focus, so without this a
     * number typed and confirmed straight away would be read as the previous one.
     */
    private void commitSpinners() {
        spnPolling.commitValue();
        spnWarning.commitValue();
        spnCritical.commitValue();
        spnMinMemoryMb.commitValue();
    }
}
