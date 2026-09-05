package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.MemoryAnalyzer;
import com.ramwatch.analysis.StateTracker;
import com.ramwatch.config.AppConfig;
import com.ramwatch.storage.EventLogger;
import com.ramwatch.storage.MemoryEvent;
import com.ramwatch.system.PollingService;
import com.ramwatch.system.SystemSampler;
import com.ramwatch.system.SystemSnapshot;
import javafx.application.Platform;

public final class DashboardController {

    private final DashboardView view;
    private final PollingService pollingService;
    private final StateTracker stateTracker = new StateTracker();
    // Mutated from the JavaFX thread, read from the polling thread.
    private volatile MemoryAnalyzer analyzer;
    private volatile EventLogger eventLogger;
    private volatile AppConfig currentConfig;
    private volatile boolean trackerResetPending;

    public DashboardController(DashboardView view, AppConfig initialConfig) {
        this.view = view;
        this.currentConfig = initialConfig;
        this.pollingService = new PollingService(new SystemSampler());
        this.analyzer = analyzerFrom(initialConfig);
        this.eventLogger = loggerFrom(initialConfig);
    }

    public void start() {
        pollingService.start(currentConfig.pollingIntervalSeconds(), this::onSnapshot);
    }

    public void stop() {
        pollingService.stop();
    }

    public void applyConfig(AppConfig config) {
        AppConfig previous = this.currentConfig;
        this.currentConfig = config;
        this.analyzer = analyzerFrom(config);

        // New thresholds mean the remembered state was judged by different rules.
        // The tracker belongs to the polling thread, so let it do the reset itself.
        if (thresholdsChanged(previous, config)) {
            trackerResetPending = true;
        }
        if (config.logFilePath().equals(previous.logFilePath())) {
            eventLogger.setEnabled(config.loggingEnabled());
        } else {
            eventLogger = loggerFrom(config);
        }

        pollingService.restart(config.pollingIntervalSeconds());
    }

    public AppConfig currentConfig() {
        return currentConfig;
    }

    private void onSnapshot(SystemSnapshot snapshot) {
        if (trackerResetPending) {
            trackerResetPending = false;
            stateTracker.reset();
        }

        AnalyzedSnapshot analyzed = analyzer.analyze(snapshot);

        // Threshold crossings only: a steady state must not fill the log.
        if (stateTracker.accept(analyzed.state())) {
            eventLogger.log(MemoryEvent.from(analyzed, snapshot.memory()));
        }

        long total = snapshot.memory().totalBytes();
        long used = snapshot.memory().usedBytes();
        long free = snapshot.memory().availableBytes();

        Platform.runLater(() -> view.update(analyzed, total, used, free));
    }

    private static boolean thresholdsChanged(AppConfig a, AppConfig b) {
        return a.warningFreePercent() != b.warningFreePercent()
                || a.criticalFreePercent() != b.criticalFreePercent();
    }

    private static MemoryAnalyzer analyzerFrom(AppConfig cfg) {
        return new MemoryAnalyzer(
                cfg.warningFreePercent(),
                cfg.criticalFreePercent(),
                cfg.minProcessMemoryBytes(),
                10
        );
    }

    private static EventLogger loggerFrom(AppConfig cfg) {
        return new EventLogger(cfg.logFilePath(), cfg.loggingEnabled());
    }
}
