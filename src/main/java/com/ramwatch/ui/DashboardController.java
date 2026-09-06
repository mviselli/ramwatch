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

/**
 * Wires the polling, analysis and storage layers to the dashboard.
 *
 * <p>This is where the two threads of the application meet. Sampling and analysis happen on
 * the polling thread; the results reach {@link DashboardView} through
 * {@link Platform#runLater}, because JavaFX nodes may only be touched on the application
 * thread. Everything the two threads share is {@code volatile} and swapped as a whole rather
 * than mutated, so a cycle in flight always sees a coherent configuration.
 *
 * <p>Only threshold crossings, not individual cycles, raise an alert or write to the log:
 * {@link StateTracker} decides what counts as a change.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class DashboardController {

    /** The screen this controller feeds; touched only on the JavaFX application thread. */
    private final DashboardView view;

    /** Drives the sampling cycles on its own background thread. */
    private final PollingService pollingService;

    /** Turns a stream of states into the transitions worth reacting to; polling thread only. */
    private final StateTracker stateTracker = new StateTracker();

    // Mutated from the JavaFX thread, read from the polling thread.

    /** Analyzer in force, replaced wholesale when the user changes the thresholds or the filter. */
    private volatile MemoryAnalyzer analyzer;

    /** Logger in force, replaced when the log file changes and merely toggled otherwise. */
    private volatile EventLogger eventLogger;

    /** Configuration currently applied. */
    private volatile AppConfig currentConfig;

    /** Set when new thresholds arrive, so the polling thread resets the tracker itself. */
    private volatile boolean trackerResetPending;

    /**
     * Builds the controller and everything it drives, without starting to poll.
     *
     * @param view          the dashboard to update; must not be {@code null}
     * @param initialConfig the configuration to start with, usually the stored one; must not
     *                      be {@code null}
     */
    public DashboardController(DashboardView view, AppConfig initialConfig) {
        this.view = view;
        this.currentConfig = initialConfig;
        this.pollingService = new PollingService(new SystemSampler());
        this.analyzer = analyzerFrom(initialConfig);
        this.eventLogger = loggerFrom(initialConfig);
    }

    /**
     * Starts polling at the configured interval.
     *
     * <p>Call it once the stage is visible: the first snapshot arrives immediately.
     */
    public void start() {
        pollingService.start(currentConfig.pollingIntervalSeconds(), this::onSnapshot);
    }

    /**
     * Stops polling and releases the background thread.
     *
     * <p>Call it when the window closes. The controller cannot be started again afterwards.
     */
    public void stop() {
        pollingService.stop();
    }

    /**
     * Applies a new configuration live, without restarting the application.
     *
     * <p>What each change costs is deliberately different: new thresholds or a new filter
     * build a fresh analyzer and re-judge the state from scratch, while merely toggling
     * logging reuses the existing logger and a new log file builds a new one. The polling
     * service is then rescheduled at the new interval.
     *
     * <p>Call this on the JavaFX application thread.
     *
     * @param config the configuration to apply; must not be {@code null}
     */
    public void applyConfig(AppConfig config) {
        AppConfig previous = this.currentConfig;
        this.currentConfig = config;
        this.analyzer = analyzerFrom(config);

        // New thresholds mean the remembered state was judged by different rules.
        // The tracker belongs to the polling thread, so let it do the reset itself.
        if (thresholdsChanged(previous, config)) {
            trackerResetPending = true;
            // The banner was raised under the old thresholds; the next cycle re-judges.
            view.hideAlert();
        }
        if (config.logFilePath().equals(previous.logFilePath())) {
            eventLogger.setEnabled(config.loggingEnabled());
        } else {
            eventLogger = loggerFrom(config);
        }

        pollingService.restart(config.pollingIntervalSeconds());
    }

    /**
     * The configuration currently in force.
     *
     * @return the applied configuration, never {@code null}
     */
    public AppConfig currentConfig() {
        return currentConfig;
    }

    /**
     * Handles one polling cycle: analyse, log a transition, then hand the result to the UI.
     *
     * <p>Runs on the polling thread. Everything the UI needs is read into local variables
     * before the hop, so the lambda never touches shared mutable state from the JavaFX thread.
     *
     * @param snapshot the raw cycle just sampled; must not be {@code null}
     */
    private void onSnapshot(SystemSnapshot snapshot) {
        if (trackerResetPending) {
            trackerResetPending = false;
            stateTracker.reset();
        }

        AnalyzedSnapshot analyzed = analyzer.analyze(snapshot);

        // Threshold crossings only: a steady state must neither fill the log nor re-alert.
        boolean transitioned = stateTracker.accept(analyzed.state());
        String logError = null;
        if (transitioned) {
            EventLogger logger = eventLogger;
            if (!logger.log(MemoryEvent.from(analyzed, snapshot.memory())) && logger.isEnabled()) {
                logError = describe(logger);
            }
        }
        String logErrorMessage = logError;

        long total = snapshot.memory().totalBytes();
        long used = snapshot.memory().usedBytes();
        long free = snapshot.memory().availableBytes();

        Platform.runLater(() -> {
            view.update(analyzed, total, used, free);
            if (transitioned) {
                view.onStateTransition(analyzed.state(), free);
                if (logErrorMessage != null) {
                    view.showLogError(logErrorMessage);
                } else {
                    view.clearLogError();
                }
            }
        });
    }

    /**
     * Builds the message shown when an event could not be written.
     *
     * @param logger the logger that failed
     * @return the log file path, followed by the failure's message when one is available
     */
    private static String describe(EventLogger logger) {
        return logger.lastError()
                .map(e -> logger.logFile() + " — " + e.getMessage())
                .orElseGet(() -> logger.logFile().toString());
    }

    /**
     * Whether the two configurations judge the memory state by different rules.
     *
     * @param a the previous configuration
     * @param b the new configuration
     * @return {@code true} if either threshold differs
     */
    private static boolean thresholdsChanged(AppConfig a, AppConfig b) {
        return a.warningFreePercent() != b.warningFreePercent()
                || a.criticalFreePercent() != b.criticalFreePercent();
    }

    /**
     * Builds the analyzer described by a configuration.
     *
     * @param cfg the configuration to read the thresholds and the filter from
     * @return a new analyzer reporting the ten heaviest processes
     */
    private static MemoryAnalyzer analyzerFrom(AppConfig cfg) {
        return new MemoryAnalyzer(
                cfg.warningFreePercent(),
                cfg.criticalFreePercent(),
                cfg.minProcessMemoryBytes(),
                10
        );
    }

    /**
     * Builds the logger described by a configuration.
     *
     * @param cfg the configuration to read the log file and the logging switch from
     * @return a new logger, already enabled or disabled as configured
     */
    private static EventLogger loggerFrom(AppConfig cfg) {
        return new EventLogger(cfg.logFilePath(), cfg.loggingEnabled());
    }
}
