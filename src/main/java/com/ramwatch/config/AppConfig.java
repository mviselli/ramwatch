package com.ramwatch.config;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable snapshot of all user-configurable settings.
 *
 * <p>One instance describes the whole configuration in force. Settings are never mutated:
 * changing one means deriving a new instance with {@link #toBuilder()}, which the application
 * then applies to the polling service and the analyzer. Every instance is validated on
 * creation, so an invalid configuration cannot exist.
 *
 * <p>Build with {@link #defaults()} or {@link #builder()};
 * {@link ConfigStore} takes care of reading and writing it to disk.
 *
 * @param pollingIntervalSeconds delay between two samples, in seconds; at least 1
 * @param warningFreePercent     free memory percentage below which the state becomes warning;
 *                               between 0 and 100 exclusive
 * @param criticalFreePercent    free memory percentage below which the state becomes critical;
 *                               between 0 and 100 exclusive and lower than
 *                               {@code warningFreePercent}
 * @param minProcessMemoryBytes  minimum resident memory, in bytes, for a process to appear in
 *                               the table; {@code 0} shows every process
 * @param loggingEnabled         whether memory events are written to the log file
 * @param logFilePath            file events are appended to; always points at a file, never at
 *                               a root directory
 * @author Michele Viselli
 * @since 1.0
 */
public record AppConfig(
        int pollingIntervalSeconds,
        double warningFreePercent,
        double criticalFreePercent,
        long minProcessMemoryBytes,
        boolean loggingEnabled,
        Path logFilePath
) {

    /** Default delay between two samples, in seconds. */
    private static final int DEFAULT_POLLING_INTERVAL = 2;

    /** Default free memory percentage triggering the warning state. */
    private static final double DEFAULT_WARNING_FREE_PERCENT = 20.0;

    /** Default free memory percentage triggering the critical state. */
    private static final double DEFAULT_CRITICAL_FREE_PERCENT = 10.0;

    /** Default minimum resident memory for a process to be listed. */
    private static final long DEFAULT_MIN_PROCESS_MEMORY_BYTES = 100L * 1024 * 1024; // 100 MB

    /** Logging is off unless the user turns it on. */
    private static final boolean DEFAULT_LOGGING_ENABLED = false;

    /** Name of the log file inside the RamWatch home directory. */
    private static final String LOG_FILE_NAME = "events.log";

    /**
     * Validates the whole configuration, including the relation between the two thresholds.
     *
     * @param pollingIntervalSeconds delay between two samples, in seconds
     * @param warningFreePercent     free memory percentage for the warning state
     * @param criticalFreePercent    free memory percentage for the critical state
     * @param minProcessMemoryBytes  minimum resident memory for a listed process, in bytes
     * @param loggingEnabled         whether logging is on
     * @param logFilePath            file events are appended to
     * @throws NullPointerException     if {@code logFilePath} is {@code null}
     * @throws IllegalArgumentException if the interval is below one second, if a percentage is
     *                                  out of range, if the critical threshold is not stricter
     *                                  than the warning one, if {@code minProcessMemoryBytes}
     *                                  is negative, or if {@code logFilePath} is a root directory
     */
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

    /**
     * Default log location: ~/.ramwatch/events.log.
     *
     * @return the absolute path of the default log file, resolved against the user's home
     *         directory at every call
     */
    public static Path defaultLogFilePath() {
        Path home = Path.of(System.getProperty("user.home"));
        return home.resolve(".ramwatch").resolve(LOG_FILE_NAME);
    }

    /**
     * The configuration used on a first run, or whenever the stored one cannot be read.
     *
     * @return a valid configuration with every setting at its default
     */
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

    /**
     * Starts building a configuration from the defaults.
     *
     * @return a fresh builder, every setting preset to its default
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts building a configuration from this one, to change a setting or two.
     *
     * <p>This record is left untouched: the builder produces a new instance.
     *
     * @return a builder preset with this configuration's values
     */
    public Builder toBuilder() {
        return new Builder()
                .pollingIntervalSeconds(pollingIntervalSeconds)
                .warningFreePercent(warningFreePercent)
                .criticalFreePercent(criticalFreePercent)
                .minProcessMemoryBytes(minProcessMemoryBytes)
                .loggingEnabled(loggingEnabled)
                .logFilePath(logFilePath);
    }

    /**
     * Fluent builder for {@link AppConfig}, so a caller can set one setting without repeating
     * the other five.
     *
     * <p>Nothing is validated while building: the checks run in {@link #build()}, which is
     * where an invalid combination is rejected.
     *
     * @author Michele Viselli
     * @since 1.0
     */
    public static final class Builder {
        /** Delay between two samples, in seconds. */
        private int pollingIntervalSeconds = DEFAULT_POLLING_INTERVAL;

        /** Free memory percentage triggering the warning state. */
        private double warningFreePercent = DEFAULT_WARNING_FREE_PERCENT;

        /** Free memory percentage triggering the critical state. */
        private double criticalFreePercent = DEFAULT_CRITICAL_FREE_PERCENT;

        /** Minimum resident memory, in bytes, for a process to be listed. */
        private long minProcessMemoryBytes = DEFAULT_MIN_PROCESS_MEMORY_BYTES;

        /** Whether memory events are written to the log file. */
        private boolean loggingEnabled = DEFAULT_LOGGING_ENABLED;

        /** File events are appended to. */
        private Path logFilePath = defaultLogFilePath();

        /** Created only through {@link AppConfig#builder()} or {@link AppConfig#toBuilder()}. */
        private Builder() {}

        /**
         * Sets the delay between two samples.
         *
         * @param v delay in seconds; at least 1
         * @return this builder
         */
        public Builder pollingIntervalSeconds(int v) { this.pollingIntervalSeconds = v; return this; }

        /**
         * Sets the warning threshold.
         *
         * @param v free memory percentage, between 0 and 100 exclusive
         * @return this builder
         */
        public Builder warningFreePercent(double v) { this.warningFreePercent = v; return this; }

        /**
         * Sets the critical threshold.
         *
         * @param v free memory percentage, between 0 and 100 exclusive and lower than the
         *          warning threshold
         * @return this builder
         */
        public Builder criticalFreePercent(double v) { this.criticalFreePercent = v; return this; }

        /**
         * Sets the process filter.
         *
         * @param v minimum resident memory in bytes; {@code 0} shows every process
         * @return this builder
         */
        public Builder minProcessMemoryBytes(long v) { this.minProcessMemoryBytes = v; return this; }

        /**
         * Turns event logging on or off.
         *
         * @param v {@code true} to append events to the log file
         * @return this builder
         */
        public Builder loggingEnabled(boolean v) { this.loggingEnabled = v; return this; }

        /**
         * Sets the log file.
         *
         * @param v path of a file, not of a root directory
         * @return this builder
         */
        public Builder logFilePath(Path v) { this.logFilePath = v; return this; }

        /**
         * Builds the configuration and validates it.
         *
         * @return the resulting configuration
         * @throws NullPointerException     if the log file path is {@code null}
         * @throws IllegalArgumentException if the values fail {@link AppConfig}'s validation
         */
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
