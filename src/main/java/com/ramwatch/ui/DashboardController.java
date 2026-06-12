package com.ramwatch.ui;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.MemoryAnalyzer;
import com.ramwatch.system.PollingService;
import com.ramwatch.system.SystemSampler;
import com.ramwatch.system.SystemSnapshot;
import javafx.application.Platform;

public final class DashboardController {

    private final DashboardView view;
    private final PollingService pollingService;
    private final MemoryAnalyzer analyzer;

    public DashboardController(DashboardView view) {
        this.view = view;
        this.pollingService = new PollingService(new SystemSampler());
        this.analyzer = MemoryAnalyzer.withDefaults();
    }

    public void start(int intervalSeconds) {
        pollingService.start(intervalSeconds, this::onSnapshot);
    }

    public void stop() {
        pollingService.stop();
    }

    private void onSnapshot(SystemSnapshot snapshot) {
        AnalyzedSnapshot analyzed = analyzer.analyze(snapshot);
        long total = snapshot.memory().totalBytes();
        long used = snapshot.memory().usedBytes();
        long free = snapshot.memory().availableBytes();

        Platform.runLater(() -> view.update(analyzed, total, used, free));
    }
}
