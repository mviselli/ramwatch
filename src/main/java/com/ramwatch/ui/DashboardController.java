package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.MemoryAnalyzer;
import com.ramwatch.config.AppConfig;
import com.ramwatch.system.PollingService;
import com.ramwatch.system.SystemSampler;
import com.ramwatch.system.SystemSnapshot;
import javafx.application.Platform;

public final class DashboardController {

    private final DashboardView view;
    private final PollingService pollingService;
    private MemoryAnalyzer analyzer;
    private AppConfig currentConfig;

    public DashboardController(DashboardView view, AppConfig initialConfig) {
        this.view = view;
        this.currentConfig = initialConfig;
        this.pollingService = new PollingService(new SystemSampler());
        this.analyzer = analyzerFrom(initialConfig);
    }

    public void start() {
        pollingService.start(currentConfig.pollingIntervalSeconds(), this::onSnapshot);
    }

    public void stop() {
        pollingService.stop();
    }

    public void applyConfig(AppConfig config) {
        this.currentConfig = config;
        this.analyzer = analyzerFrom(config);
        pollingService.restart(config.pollingIntervalSeconds());
    }

    public AppConfig currentConfig() {
        return currentConfig;
    }

    private void onSnapshot(SystemSnapshot snapshot) {
        AnalyzedSnapshot analyzed = analyzer.analyze(snapshot);
        long total = snapshot.memory().totalBytes();
        long used = snapshot.memory().usedBytes();
        long free = snapshot.memory().availableBytes();

        Platform.runLater(() -> view.update(analyzed, total, used, free));
    }

    private static MemoryAnalyzer analyzerFrom(AppConfig cfg) {
        return new MemoryAnalyzer(
                cfg.warningFreePercent(),
                cfg.criticalFreePercent(),
                cfg.minProcessMemoryBytes(),
                10
        );
    }
}
