package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemoryFormatter;
import com.ramwatch.system.ProcessSnapshot;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * The main screen: memory figures, usage gauge, history chart and process table.
 *
 * <p>The layout is built once in the constructor; {@link #update} then only changes the text
 * and the values of the existing nodes, so a polling cycle never recreates the scene graph.
 * The refresh goes further and skips the nodes whose appearance would not change at all: the
 * process rows that {@link ProcessRowDiff} judges unchanged, the total memory that never
 * moves, and the state styling while the state holds.
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

    /** Total physical memory, formatted; the value of its stat tile. */
    private final Label lblTotal = new Label();

    /** Memory in use, formatted; the value of its stat tile. */
    private final Label lblUsed = new Label();

    /** Memory available, formatted; the value of its stat tile. */
    private final Label lblFree = new Label();

    /** Ring gauge carrying the usage percentage and the current state. */
    private final RamGauge gauge = new RamGauge();

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

    /** Memory of the heaviest process of the latest cycle, which the share bars scale to; never zero. */
    private long currentHeaviestProcessBytes = 1;

    // refresh memo

    /** Total memory already written into {@link #lblTotal}; negative until the first cycle. */
    private long shownTotalBytes = -1;

    /** Reused by {@link #showLogError} so a failure never builds a second tooltip. */
    private final Tooltip logErrorTooltip = new Tooltip();

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
        // Physical memory does not change while the app runs, so this text is written once.
        if (totalBytes != shownTotalBytes) {
            shownTotalBytes = totalBytes;
            lblTotal.setText(MemoryFormatter.formatBytes(totalBytes));
        }
        lblUsed.setText(MemoryFormatter.formatBytes(usedBytes));
        lblFree.setText(MemoryFormatter.formatBytes(freeBytes));

        gauge.update(analyzed.usedPercent(), analyzed.state());

        currentTotalBytes = totalBytes > 0 ? totalBytes : 1;
        currentHeaviestProcessBytes = heaviestOf(analyzed.topConsumers());
        ramChart.addSample(analyzed.usedPercent());
        refreshProcessRows(analyzed.topConsumers());
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
        logErrorTooltip.setText(detail);
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
     * Builds the header card: title, toolbar buttons, usage gauge and the three stat tiles.
     *
     * @return the assembled card
     */
    private VBox buildHeaderCard() {
        Label title = new Label("RamWatch");
        title.getStyleClass().add("label-app-title");

        themeBtn.getStyleClass().add("toolbar-button");
        themeBtn.setOnAction(e -> { if (themeToggleCallback != null) themeToggleCallback.run(); });

        settingsBtn.getStyleClass().add("toolbar-button");
        settingsBtn.setOnAction(e -> { if (settingsCallback != null) settingsCallback.run(); });

        exportBtn.getStyleClass().add("toolbar-button");
        exportBtn.setOnAction(e -> { if (exportCallback != null) exportCallback.run(); });

        lblLogError.getStyleClass().add("label-log-error");
        lblLogError.setTooltip(logErrorTooltip);
        lblLogError.setVisible(false);
        lblLogError.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, lblLogError, spacer, exportBtn, themeBtn, settingsBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        // The tiles share the width left over by the gauge, so they stay even as it resizes.
        HBox tiles = new HBox(12,
                buildStatTile("TOTAL", lblTotal),
                buildStatTile("USED", lblUsed),
                buildStatTile("FREE", lblFree));
        tiles.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tiles, Priority.ALWAYS);

        HBox readout = new HBox(24, gauge, tiles);
        readout.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(18, titleRow, readout);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Builds one stat tile: a caption above the figure it names.
     *
     * <p>The caption is fixed, the value label is the one {@link #update} writes into.
     *
     * @param caption the fixed heading, shown small and uppercase
     * @param value   the label carrying the figure; kept by the caller to update it
     * @return the assembled tile
     */
    private static VBox buildStatTile(String caption, Label value) {
        Label lblCaption = new Label(caption);
        lblCaption.getStyleClass().add("stat-caption");
        value.getStyleClass().add("stat-value");

        VBox tile = new VBox(3, lblCaption, value);
        tile.getStyleClass().add("stat-tile");
        // The tiles stretch to the gauge's height, so their content is centred rather than
        // pinned to the top of a box far taller than the two lines it holds.
        tile.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
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
        Label title = new Label("RAM USAGE HISTORY");
        title.getStyleClass().add("section-title");

        VBox card = new VBox(10, title, ramChart);
        card.getStyleClass().add("card");
        return card;
    }

    /**
     * Builds the process table: name, PID, share of the total and memory used.
     *
     * <p>The share is computed per cell against the latest total rather than stored on the
     * model, so {@link ProcessSnapshot} stays a plain reading with no display concerns. The
     * share column takes the whole row as its value because {@link ProcessShareCell} needs the
     * byte count, not a string already formatted for it.
     *
     * @return the assembled card
     */
    @SuppressWarnings("unchecked")
    private VBox buildTableCard() {
        Label title = new Label("TOP PROCESSES");
        title.getStyleClass().add("section-title");

        TableColumn<ProcessSnapshot, String> colName = new TableColumn<>("Process");
        colName.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        colName.setCellFactory(col -> new ProcessNameCell());
        colName.setPrefWidth(220);

        TableColumn<ProcessSnapshot, String> colPid = new TableColumn<>("PID");
        colPid.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(cell.getValue().pid())));
        // Alignment is a layout concern, not a themed one, and JavaFX splits it in two: the
        // inline style reaches the cells, the style class reaches the column header.
        colPid.setStyle("-fx-alignment: CENTER-RIGHT;");
        colPid.getStyleClass().add("numeric-column");
        colPid.setPrefWidth(70);

        TableColumn<ProcessSnapshot, ProcessSnapshot> colShare = new TableColumn<>("Share");
        colShare.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        colShare.setCellFactory(col ->
                new ProcessShareCell(() -> currentTotalBytes, () -> currentHeaviestProcessBytes));
        colShare.setSortable(false);
        colShare.setPrefWidth(160);

        TableColumn<ProcessSnapshot, String> colMemory = new TableColumn<>("Memory");
        colMemory.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        MemoryFormatter.formatBytes(cell.getValue().usedMemoryBytes())));
        colMemory.setStyle("-fx-alignment: CENTER-RIGHT;");
        colMemory.getStyleClass().add("numeric-column");
        colMemory.setPrefWidth(90);

        processTable.getColumns().addAll(colName, colPid, colShare, colMemory);
        processTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        processTable.setPlaceholder(new Label("No processes above threshold"));
        processTable.setFixedCellSize(34);
        VBox.setVgrow(processTable, Priority.ALWAYS);

        VBox card = new VBox(10, title, processTable);
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * The memory of the heaviest process of a cycle, which the share bars are scaled to.
     *
     * @param ranked the processes of the cycle, sorted by memory descending; must not be
     *               {@code null}
     * @return the memory of the first process, in bytes, or {@code 1} when the list is empty or
     *         its heaviest process reports nothing, so callers can divide by it safely
     */
    private static long heaviestOf(List<ProcessSnapshot> ranked) {
        if (ranked.isEmpty()) {
            return 1;
        }
        return Math.max(1, ranked.get(0).usedMemoryBytes());
    }

    // ── table refresh ─────────────────────────────────────────

    /**
     * Brings the process rows in line with the latest cycle, touching only what changed.
     *
     * @param latest the ranked processes of the latest cycle; must not be {@code null}
     */
    private void refreshProcessRows(List<ProcessSnapshot> latest) {
        ProcessRowDiff.apply(processTable.getItems(), latest);
    }
}
