package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemoryFormatter;
import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class DashboardView extends BorderPane {

    // --- header ---
    private final Label lblTotal = new Label();
    private final Label lblUsed = new Label();
    private final Label lblFree = new Label();
    private final Label lblPercent = new Label();
    private final Label lblState = new Label();
    private final ProgressBar ramBar = new ProgressBar(0);

    // --- grafico storico ---
    private final RamChart ramChart = new RamChart();

    // --- tabella processi ---
    private final TableView<ProcessSnapshot> processTable = new TableView<>();

    public DashboardView() {
        setPadding(new Insets(16));
        setTop(buildHeader());
        setCenter(buildCenter());
    }

    // --- aggiornamento pubblico ---

    public void update(AnalyzedSnapshot analyzed, long totalBytes, long usedBytes, long freeBytes) {
        lblTotal.setText("Total:  " + MemoryFormatter.formatBytes(totalBytes));
        lblUsed.setText("Used:   " + MemoryFormatter.formatBytes(usedBytes));
        lblFree.setText("Free:   " + MemoryFormatter.formatBytes(freeBytes));
        lblPercent.setText(analyzed.usedPercent() + "%");

        ramBar.setProgress(analyzed.usedPercent() / 100.0);
        applyStateStyle(analyzed.state());

        ramChart.addSample(analyzed.usedPercent());
        processTable.getItems().setAll(analyzed.topConsumers());
    }

    // --- costruzione layout ---

    private VBox buildHeader() {
        // titolo app
        Label title = new Label("RamWatch");
        title.setFont(Font.font("System", FontWeight.BOLD, 20));

        // metriche RAM
        lblTotal.setStyle("-fx-font-size: 13px;");
        lblUsed.setStyle("-fx-font-size: 13px;");
        lblFree.setStyle("-fx-font-size: 13px;");

        HBox metrics = new HBox(24, lblTotal, lblUsed, lblFree);
        metrics.setAlignment(Pos.CENTER_LEFT);

        // percentuale + stato
        lblPercent.setFont(Font.font("System", FontWeight.BOLD, 15));
        lblState.setFont(Font.font("System", FontWeight.BOLD, 13));
        HBox statusRow = new HBox(12, lblPercent, lblState);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // barra RAM
        ramBar.setMaxWidth(Double.MAX_VALUE);
        ramBar.setPrefHeight(14);
        HBox.setHgrow(ramBar, Priority.ALWAYS);

        VBox header = new VBox(8, title, metrics, statusRow, ramBar);
        header.setPadding(new Insets(0, 0, 16, 0));
        return header;
    }

    private VBox buildCenter() {
        VBox tableSection = buildTable();
        VBox.setVgrow(tableSection, Priority.ALWAYS);
        VBox center = new VBox(12, ramChart, tableSection);
        VBox.setVgrow(center, Priority.ALWAYS);
        return center;
    }

    @SuppressWarnings("unchecked")
    private VBox buildTable() {
        Label tableTitle = new Label("Top Processes");
        tableTitle.setFont(Font.font("System", FontWeight.BOLD, 13));

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

        processTable.getColumns().addAll(colName, colPid, colMemory);
        processTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        processTable.setPlaceholder(new Label("No processes above threshold"));
        VBox.setVgrow(processTable, Priority.ALWAYS);

        VBox tableSection = new VBox(8, tableTitle, processTable);
        return tableSection;
    }

    private void applyStateStyle(RamState state) {
        String color = switch (state) {
            case STABLE -> "#4caf50";
            case WARNING -> "#ff9800";
            case CRITICAL -> "#f44336";
        };
        lblState.setText(state.name());
        lblState.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        ramBar.setStyle("-fx-accent: " + color + ";");
    }
}
