package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemoryFormatter;
import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * The main screen: memory figures, usage gauge, history chart and process table.
 *
 * <p>The layout is built once in the constructor; {@link #update} then only changes the text
 * and the values of the existing nodes, so a polling cycle never recreates the scene graph.
 * State is conveyed through CSS style classes rather than hardcoded colours, which is what
 * lets the light and dark stylesheets restyle the whole window on their own.
 *
 * <p>The view owns no logic of its own: what to show is decided by
 * {@link DashboardController}, and the toolbar buttons merely run the callbacks it registers.
 *
 * <p>Every method must be called on the JavaFX application thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class DashboardView extends BorderPane {

    // header

    /** Total physical memory, formatted. */
    private final Label lblTotal   = new Label();

    /** Memory in use, formatted. */
    private final Label lblUsed    = new Label();

    /** Memory available, formatted. */
    private final Label lblFree    = new Label();

    /** Usage percentage shown next to the state. */
    private final Label lblPercent = new Label();

    /** Current state, styled by the CSS class matching it. */
    private final Label lblState   = new Label();

    /** Usage gauge, coloured by state. */
    private final ProgressBar ramBar = new ProgressBar(0);

    // alert banner

    /** Text of the threshold-crossing alert. */
    private final Label lblAlert = new Label();

    /** Shown when the event log cannot be written; the detail lands in its tooltip. */
    private final Label lblLogError = new Label("⚠  Log write failed");

    /** Dismissible banner holding {@link #lblAlert}; hidden while the state is stable. */
    private final HBox alertBanner = buildAlertBanner();

    // chart

    /** Recent usage history, capped at a fixed number of samples. */
    private final RamChart ramChart = new RamChart();

    // table

    /** The heaviest processes of the latest cycle. */
    private final TableView<ProcessSnapshot> processTable = new TableView<>();

    /** Total memory of the latest cycle, used to compute each process's share; never zero. */
    private long currentTotalBytes = 1;

    // theme

    /** Whether the dark stylesheet is the one applied. */
    private boolean darkMode = false;

    /** Runs when the user asks to switch theme; {@code null} until registered. */
    private Runnable themeToggleCallback;

    /** Toolbar button toggling the theme; its label follows the current one. */
    private final Button themeBtn = new Button("☾  Dark");

    // settings

    /** Runs when the user opens the settings dialog; {@code null} until registered. */
    private Runnable settingsCallback;

    /** Toolbar button opening the settings dialog. */
    private final Button settingsBtn = new Button("⚙  Settings");

    // csv export

    /** Runs when the user asks to export the events; {@code null} until registered. */
    private Runnable exportCallback;

    /** Toolbar button starting the CSV export. */
    private final Button exportBtn = new Button("⇩  Export CSV");

    /**
     * Builds the whole layout: header card, alert banner, chart and process table.
     */
    public DashboardView() {
        setPadding(new Insets(16));
        setTop(buildTop());
        setCenter(buildCenter());
    }

    // ── public API ───────────────────────────────────────────

    /**
     * Renders one analysed cycle, reusing every node already on screen.
     *
     * <p>The raw byte counts come alongside the analysis because the record carries
     * percentages only, while the header shows absolute sizes.
     *
     * @param analyzed   the analysed cycle to display; must not be {@code null}
     * @param totalBytes total physical memory, in bytes
     * @param usedBytes  memory in use, in bytes
     * @param freeBytes  memory available, in bytes
     */
    public void update(AnalyzedSnapshot analyzed, long totalBytes, long usedBytes, long freeBytes) {
        lblTotal.setText("Total  " + MemoryFormatter.formatBytes(totalBytes));
        lblUsed.setText("Used  " + MemoryFormatter.formatBytes(usedBytes));
        lblFree.setText("Free  " + MemoryFormatter.formatBytes(freeBytes));
        lblPercent.setText(analyzed.usedPercent() + "%");

        ramBar.setProgress(analyzed.usedPercent() / 100.0);
        applyStateStyle(analyzed.state());

        currentTotalBytes = totalBytes > 0 ? totalBytes : 1;
        ramChart.addSample(analyzed.usedPercent());
        processTable.getItems().setAll(analyzed.topConsumers());
    }

    /**
     * Shows the alert banner for a threshold crossing, or hides it on recovery.
     * Called on state transitions only, so a steady state never re-raises the alert.
     *
     * @param state     the state just entered
     * @param freeBytes memory available at the crossing, in bytes; shown in the message
     */
    public void onStateTransition(RamState state, long freeBytes) {
        if (state == RamState.STABLE) {
            hideAlert();
            return;
        }
        alertBanner.getStyleClass().removeAll("alert-warning", "alert-critical");
        alertBanner.getStyleClass().add(state == RamState.CRITICAL ? "alert-critical" : "alert-warning");
        lblAlert.setText((state == RamState.CRITICAL ? "Critical: " : "Warning: ")
                + "free RAM down to " + MemoryFormatter.formatBytes(freeBytes) + ".");
        alertBanner.setVisible(true);
        alertBanner.setManaged(true);
    }

    /**
     * Warns that the event log is unwritable; {@code detail} lands in the tooltip.
     *
     * @param detail the failing path and, when known, the reason it failed
     */
    public void showLogError(String detail) {
        lblLogError.setTooltip(new Tooltip(detail));
        lblLogError.setVisible(true);
        lblLogError.setManaged(true);
    }

    /** Removes the log-failure warning after a successful write. */
    public void clearLogError() {
        lblLogError.setVisible(false);
        lblLogError.setManaged(false);
    }

    /**
     * Hides the alert banner, on recovery or when the user dismisses it.
     */
    public void hideAlert() {
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }

    /**
     * Registers what to run when the user switches theme.
     *
     * <p>Swapping the stylesheet belongs to the owner of the scene, not to this view.
     *
     * @param callback the action to run, or {@code null} to disable the button's effect
     */
    public void setThemeToggleCallback(Runnable callback) {
        this.themeToggleCallback = callback;
    }

    /**
     * Registers what to run when the user opens the settings.
     *
     * @param callback the action to run, or {@code null} to disable the button's effect
     */
    public void setSettingsCallback(Runnable callback) {
        this.settingsCallback = callback;
    }

    /**
     * Registers what to run when the user asks for a CSV export.
     *
     * @param callback the action to run, or {@code null} to disable the button's effect
     */
    public void setExportCallback(Runnable callback) {
        this.exportCallback = callback;
    }

    /**
     * Which theme the view believes is applied.
     *
     * @return {@code true} when the dark stylesheet is in use
     */
    public boolean isDarkMode() {
        return darkMode;
    }

    /**
     * Records the theme in force and relabels the toggle accordingly.
     *
     * <p>This only updates the button: applying the stylesheet is the caller's job.
     *
     * @param dark {@code true} when the dark stylesheet has been applied
     */
    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        themeBtn.setText(dark ? "☀  Light" : "☾  Dark");
    }

    // ── layout ───────────────────────────────────────────────

    /**
     * Builds the top region: the header card with the alert banner underneath.
     *
     * @return the assembled top container
     */
    private VBox buildTop() {
        VBox top = new VBox(12, buildHeaderCard(), alertBanner);
        BorderPane.setMargin(top, new Insets(0, 0, 16, 0));
        return top;
    }

    /**
     * Builds the alert banner, hidden and unmanaged so it takes no space until raised.
     *
     * @return the assembled banner
     */
    private HBox buildAlertBanner() {
        lblAlert.getStyleClass().add("alert-text");

        Button dismissBtn = new Button("✕");
        dismissBtn.getStyleClass().add("alert-dismiss");
        dismissBtn.setOnAction(e -> hideAlert());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox banner = new HBox(10, lblAlert, spacer, dismissBtn);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("alert-banner");
        banner.setVisible(false);
        banner.setManaged(false);
        return banner;
    }

    /**
     * Builds the header card: title, toolbar buttons, memory figures and usage gauge.
     *
     * @return the assembled card
     */
    private VBox buildHeaderCard() {
        Label title = new Label("RamWatch");
        title.getStyleClass().add("label-app-title");

        themeBtn.getStyleClass().add("theme-toggle");
        themeBtn.setOnAction(e -> { if (themeToggleCallback != null) themeToggleCallback.run(); });

        settingsBtn.getStyleClass().add("theme-toggle");
        settingsBtn.setOnAction(e -> { if (settingsCallback != null) settingsCallback.run(); });

        exportBtn.getStyleClass().add("theme-toggle");
        exportBtn.setOnAction(e -> { if (exportCallback != null) exportCallback.run(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, title, spacer, exportBtn, themeBtn, settingsBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        lblTotal.getStyleClass().add("label-metric");
        lblUsed.getStyleClass().add("label-metric");
        lblFree.getStyleClass().add("label-metric");

        HBox metrics = new HBox(24, lblTotal, lblUsed, lblFree);
        metrics.setAlignment(Pos.CENTER_LEFT);

        lblPercent.getStyleClass().add("label-percent");

        lblLogError.getStyleClass().add("label-log-error");
        lblLogError.setVisible(false);
        lblLogError.setManaged(false);

        HBox statusRow = new HBox(10, lblPercent, lblState, lblLogError);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        ramBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(ramBar, Priority.ALWAYS);

        VBox card = new VBox(10, titleRow, metrics, statusRow, ramBar);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Builds the centre region, giving the table the vertical space the chart does not need.
     *
     * @return the assembled centre container
     */
    private VBox buildCenter() {
        VBox chartCard = buildChartCard();
        VBox tableCard = buildTableCard();
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox center = new VBox(16, chartCard, tableCard);
        VBox.setVgrow(center, Priority.ALWAYS);
        return center;
    }

    /**
     * Builds the card holding the usage history chart.
     *
     * @return the assembled card
     */
    private VBox buildChartCard() {
        Label title = new Label("RAM Usage History");
        title.getStyleClass().add("section-title");

        VBox card = new VBox(8, title, ramChart);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Builds the process table: name, PID, memory and share of the total.
     *
     * <p>The share is computed per cell against the latest total rather than stored on the
     * model, so {@link ProcessSnapshot} stays a plain reading with no display concerns.
     *
     * @return the assembled card
     */
    @SuppressWarnings("unchecked")
    private VBox buildTableCard() {
        Label title = new Label("Top Processes");
        title.getStyleClass().add("section-title");

        TableColumn<ProcessSnapshot, String> colName = new TableColumn<>("Process");
        colName.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        colName.setPrefWidth(200);

        TableColumn<ProcessSnapshot, String> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(cell.getValue().pid())));
        colPid.setPrefWidth(70);

        TableColumn<ProcessSnapshot, String> colMemory = new TableColumn<>("Memory");
        colMemory.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        MemoryFormatter.formatBytes(cell.getValue().usedMemoryBytes())));
        colMemory.setPrefWidth(100);

        TableColumn<ProcessSnapshot, String> colPct = new TableColumn<>("%");
        colPct.setCellValueFactory(cell -> {
            double pct = cell.getValue().usedMemoryBytes() * 100.0 / currentTotalBytes;
            return new javafx.beans.property.SimpleStringProperty(
                    String.format("%.1f%%", pct));
        });
        colPct.setPrefWidth(60);

        processTable.getColumns().addAll(colName, colPid, colMemory, colPct);
        processTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        processTable.setPlaceholder(new Label("No processes above threshold"));
        VBox.setVgrow(processTable, Priority.ALWAYS);

        VBox card = new VBox(8, title, processTable);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    // ── state styling ─────────────────────────────────────────

    /**
     * Restyles the state label and the gauge for the given state.
     *
     * <p>The previous state's classes are removed first, so the styles never stack up over
     * successive cycles.
     *
     * @param state the state to reflect; must not be {@code null}
     */
    private void applyStateStyle(RamState state) {
        lblState.getStyleClass().removeAll("label-state-stable", "label-state-warning", "label-state-critical");
        ramBar.getStyleClass().removeAll("stable", "warning", "critical");

        String key = switch (state) {
            case STABLE -> "stable";
            case WARNING -> "warning";
            case CRITICAL -> "critical";
        };
        lblState.getStyleClass().add("label-state-" + key);
        ramBar.getStyleClass().add(key);
        lblState.setText(state.name());
    }
}
