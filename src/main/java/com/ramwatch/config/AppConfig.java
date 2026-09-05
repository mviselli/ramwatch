package com.ramwatch.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable snapshot of all user-configurable settings.
 * Build with {@link #defaults()} or {@link #builder()}.
 */
public record AppConfig(
        int pollingIntervalSeconds,
        double warningFreePercent,
        double criticalFreePercent,
        long minProcessMemoryBytes,
        boolean loggingEnabled,
        Path logFilePath
) {

    private static final int DEFAULT_POLLING_INTERVAL = 2;
    private static final double DEFAULT_WARNING_FREE_PERCENT = 20.0;
    private static final double DEFAULT_CRITICAL_FREE_PERCENT = 10.0;
    private static final long DEFAULT_MIN_PROCESS_MEMORY_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final boolean DEFAULT_LOGGING_ENABLED = false;
    private static final String LOG_FILE_NAME = "events.log";

    public AppConfig {
        if (pollingIntervalSeconds < 1) {
            throw new IllegalArgumentException("pollingIntervalSeconds must be >= 1");
        }
        if (warningFreePercent <= 0 || warningFreePercent >= 100) {
            throw new IllegalArgumentException("warningFreePercent must be between 0 and 100 exclusive");
        }
        if (criticalFreePercent <= 0 || criticalFreePercent >= 100) {
            throw new IllegalArgumentException("criticalFreePercent must be between 0 and 100 exclusive");
        }
        if (criticalFreePercent >= warningFreePercent) {
            throw new IllegalArgumentException("criticalFreePercent must be lower than warningFreePercent");
        }
        if (minProcessMemoryBytes < 0) {
            throw new IllegalArgumentException("minProcessMemoryBytes must not be negative");
        }
        Objects.requireNonNull(logFilePath, "logFilePath must not be null");
        if (logFilePath.getFileName() == null) {
            throw new IllegalArgumentException("logFilePath must point to a file, not a root directory");
        }
    }

    /** Default log location: ~/.ramwatch/events.log. */
    public static Path defaultLogFilePath() {
        Path home = Path.of(System.getProperty("user.home"));
        return home.resolve(".ramwatch").resolve(LOG_FILE_NAME);
    }

    public static AppConfig defaults() {
        return new AppConfig(
                DEFAULT_POLLING_INTERVAL,
                DEFAULT_WARNING_FREE_PERCENT,
                DEFAULT_CRITICAL_FREE_PERCENT,
                DEFAULT_MIN_PROCESS_MEMORY_BYTES,
                DEFAULT_LOGGING_ENABLED,
                defaultLogFilePath()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .pollingIntervalSeconds(pollingIntervalSeconds)
                .warningFreePercent(warningFreePercent)
                .criticalFreePercent(criticalFreePercent)
                .minProcessMemoryBytes(minProcessMemoryBytes)
                .loggingEnabled(loggingEnabled)
                .logFilePath(logFilePath);
    }

    public static final class Builder {
        private int pollingIntervalSeconds = DEFAULT_POLLING_INTERVAL;
        private double warningFreePercent = DEFAULT_WARNING_FREE_PERCENT;
        private double criticalFreePercent = DEFAULT_CRITICAL_FREE_PERCENT;
        private long minProcessMemoryBytes = DEFAULT_MIN_PROCESS_MEMORY_BYTES;
        private boolean loggingEnabled = DEFAULT_LOGGING_ENABLED;
        private Path logFilePath = defaultLogFilePath();

        private Builder() {}

        public Builder pollingIntervalSeconds(int v) { this.pollingIntervalSeconds = v; return this; }
        public Builder warningFreePercent(double v) { this.warningFreePercent = v; return this; }
        public Builder criticalFreePercent(double v) { this.criticalFreePercent = v; return this; }
        public Builder minProcessMemoryBytes(long v) { this.minProcessMemoryBytes = v; return this; }
        public Builder loggingEnabled(boolean v) { this.loggingEnabled = v; return this; }
        public Builder logFilePath(Path v) { this.logFilePath = v; return this; }

        public AppConfig build() {
            return new AppConfig(
                    pollingIntervalSeconds,
                    warningFreePercent,
                    criticalFreePercent,
                    minProcessMemoryBytes,
                    loggingEnabled,
                    logFilePath
            );
        }
    }
}
