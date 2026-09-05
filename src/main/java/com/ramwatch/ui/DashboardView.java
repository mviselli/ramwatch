package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemoryFormatter;
import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class DashboardView extends BorderPane {

    // header
    private final Label lblTotal   = new Label();
    private final Label lblUsed    = new Label();
    private final Label lblFree    = new Label();
    private final Label lblPercent = new Label();
    private final Label lblState   = new Label();
    private final ProgressBar ramBar = new ProgressBar(0);

    // alert banner
    private final Label lblAlert = new Label();
    private final HBox alertBanner = buildAlertBanner();

    // chart
    private final RamChart ramChart = new RamChart();

    // table
    private final TableView<ProcessSnapshot> processTable = new TableView<>();
    private long currentTotalBytes = 1;

    // theme
    private boolean darkMode = false;
    private Runnable themeToggleCallback;
    private final Button themeBtn = new Button("☾  Dark");

    // settings
    private Runnable settingsCallback;
    private final Button settingsBtn = new Button("⚙  Settings");

    public DashboardView() {
        setPadding(new Insets(16));
        setTop(buildTop());
        setCenter(buildCenter());
    }

    // ── public API ───────────────────────────────────────────

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

    public void hideAlert() {
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }

    public void setThemeToggleCallback(Runnable callback) {
        this.themeToggleCallback = callback;
    }

    public void setSettingsCallback(Runnable callback) {
        this.settingsCallback = callback;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean dark) {
        this.darkMode = dark;
        themeBtn.setText(dark ? "☀  Light" : "☾  Dark");
    }

    // ── layout ───────────────────────────────────────────────

    private VBox buildTop() {
        VBox top = new VBox(12, buildHeaderCard(), alertBanner);
        BorderPane.setMargin(top, new Insets(0, 0, 16, 0));
        return top;
    }

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

    private VBox buildHeaderCard() {
        Label title = new Label("RamWatch");
        title.getStyleClass().add("label-app-title");

        themeBtn.getStyleClass().add("theme-toggle");
        themeBtn.setOnAction(e -> { if (themeToggleCallback != null) themeToggleCallback.run(); });

        settingsBtn.getStyleClass().add("theme-toggle");
        settingsBtn.setOnAction(e -> { if (settingsCallback != null) settingsCallback.run(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, title, spacer, themeBtn, settingsBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        lblTotal.getStyleClass().add("label-metric");
        lblUsed.getStyleClass().add("label-metric");
        lblFree.getStyleClass().add("label-metric");

        HBox metrics = new HBox(24, lblTotal, lblUsed, lblFree);
        metrics.setAlignment(Pos.CENTER_LEFT);

        lblPercent.getStyleClass().add("label-percent");
        HBox statusRow = new HBox(10, lblPercent, lblState);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        ramBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(ramBar, Priority.ALWAYS);

        VBox card = new VBox(10, titleRow, metrics, statusRow, ramBar);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildCenter() {
        VBox chartCard = buildChartCard();
        VBox tableCard = buildTableCard();
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox center = new VBox(16, chartCard, tableCard);
        VBox.setVgrow(center, Priority.ALWAYS);
        return center;
    }

    private VBox buildChartCard() {
        Label title = new Label("RAM Usage History");
        title.getStyleClass().add("section-title");

        VBox card = new VBox(8, title, ramChart);
        card.getStyleClass().add("card");
        return card;
    }

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
