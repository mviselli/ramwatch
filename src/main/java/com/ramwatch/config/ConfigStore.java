package com.ramwatch.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists {@link AppConfig} to ~/.ramwatch/config.properties.
 *
 * <p>Reading never fails: a missing file, an unreadable one or a value that makes no sense
 * all resolve to a usable configuration. The fallback is per field, so a single corrupted
 * entry costs only that setting, not the whole file; only a configuration that is invalid as
 * a whole, such as a critical threshold above the warning one, falls back entirely to
 * {@link AppConfig#defaults()}. A malformed configuration must never stop the app from
 * starting.
 *
 * <p>Writing, by contrast, reports its errors: the caller has to know that the user's choice
 * was not saved.
 *
 * <p>Sizes are stored in megabytes for readability and converted to bytes on the way in.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class ConfigStore {

    /** Property key for the polling interval, in seconds. */
    static final String KEY_POLLING_INTERVAL = "polling.intervalSeconds";

    /** Property key for the warning threshold, as a free memory percentage. */
    static final String KEY_WARNING_FREE_PERCENT = "threshold.warningFreePercent";

    /** Property key for the critical threshold, as a free memory percentage. */
    static final String KEY_CRITICAL_FREE_PERCENT = "threshold.criticalFreePercent";

    /** Property key for the process filter, stored in megabytes. */
    static final String KEY_MIN_PROCESS_MEMORY_MB = "process.minMemoryMb";

    /** Property key for the logging switch. */
    static final String KEY_LOGGING_ENABLED = "logging.enabled";

    /** Property key for the log file path. */
    static final String KEY_LOG_FILE_PATH = "logging.filePath";

    /** File this store reads from and writes to; need not exist yet. */
    private final Path configFile;

    /**
     * Creates a store bound to a specific file, mostly useful for tests.
     *
     * <p>The file is not touched here: it is read on {@link #load()} and created on
     * {@link #save(AppConfig)}.
     *
     * @param configFile file to read from and write to; must not be {@code null}
     */
    public ConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    /**
     * Creates a store at the standard location, ~/.ramwatch/config.properties.
     *
     * @return the store the application uses at startup
     */
    public static ConfigStore atDefaultLocation() {
        Path home = Path.of(System.getProperty("user.home"));
        return new ConfigStore(home.resolve(".ramwatch").resolve("config.properties"));
    }

    /**
     * Loads the stored configuration, falling back to the defaults for anything unusable.
     *
     * @return a valid configuration, never {@code null}; the defaults when the file is absent,
     *         unreadable, or holds a combination of values that would not validate
     */
    public AppConfig load() {
        if (!Files.exists(configFile)) {
            return AppConfig.defaults();
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            Properties props = new Properties();
            props.load(in);
            return parse(props);
        } catch (IOException | IllegalArgumentException e) {
            return AppConfig.defaults();
        }
    }

    /**
     * Writes the configuration, creating the parent directory if needed.
     *
     * <p>The previous content is replaced. The failure is deliberately propagated so the
     * settings screen can tell the user the preferences were not saved.
     *
     * @param config the configuration to store; must not be {@code null}
     * @throws IOException          if the directory or the file cannot be written
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public void save(AppConfig config) throws IOException {
        Files.createDirectories(configFile.getParent());
        Properties props = new Properties();
        props.setProperty(KEY_POLLING_INTERVAL, String.valueOf(config.pollingIntervalSeconds()));
        props.setProperty(KEY_WARNING_FREE_PERCENT, String.valueOf(config.warningFreePercent()));
        props.setProperty(KEY_CRITICAL_FREE_PERCENT, String.valueOf(config.criticalFreePercent()));
        props.setProperty(KEY_MIN_PROCESS_MEMORY_MB, String.valueOf(config.minProcessMemoryBytes() / (1024L * 1024)));
        props.setProperty(KEY_LOGGING_ENABLED, String.valueOf(config.loggingEnabled()));
        props.setProperty(KEY_LOG_FILE_PATH, config.logFilePath().toString());
        try (OutputStream out = Files.newOutputStream(configFile)) {
            props.store(out, "RamWatch configuration");
        }
    }

    /**
     * Builds a configuration from the raw properties, replacing each unusable entry with its
     * own default.
     *
     * @param props the properties read from the file
     * @return the resulting configuration
     * @throws IllegalArgumentException if the recovered values still do not form a valid
     *                                  configuration; {@link #load()} turns this into the defaults
     */
    private static AppConfig parse(Properties props) {
        return AppConfig.builder()
                .pollingIntervalSeconds(parseInt(props, KEY_POLLING_INTERVAL, 2))
                .warningFreePercent(parseDouble(props, KEY_WARNING_FREE_PERCENT, 20.0))
                .criticalFreePercent(parseDouble(props, KEY_CRITICAL_FREE_PERCENT, 10.0))
                .minProcessMemoryBytes(parseLongMb(props, KEY_MIN_PROCESS_MEMORY_MB, 100L))
                .loggingEnabled(parseBoolean(props, KEY_LOGGING_ENABLED, false))
                .logFilePath(parsePath(props, KEY_LOG_FILE_PATH))
                .build();
    }

    /**
     * Reads an integer property.
     *
     * @param p        the properties to read from
     * @param key      the property key
     * @param fallback value returned when the key is missing or not a number
     * @return the parsed value, or {@code fallback}
     */
    private static int parseInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Reads a decimal property.
     *
     * @param p        the properties to read from
     * @param key      the property key
     * @param fallback value returned when the key is missing or not a number
     * @return the parsed value, or {@code fallback}
     */
    private static double parseDouble(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Reads a size stored in megabytes and converts it to bytes.
     *
     * @param p          the properties to read from
     * @param key        the property key
     * @param fallbackMb size in megabytes used when the key is missing or not a number
     * @return the size in bytes
     */
    private static long parseLongMb(Properties p, String key, long fallbackMb) {
        String v = p.getProperty(key);
        if (v == null) return fallbackMb * 1024 * 1024;
        try { return Long.parseLong(v.strip()) * 1024 * 1024; } catch (NumberFormatException e) { return fallbackMb * 1024 * 1024; }
    }

    /**
     * Reads a file path property.
     *
     * <p>A blank value, a syntactically invalid path and a path naming a root directory are
     * all unusable as a log file, so they resolve to the default location.
     *
     * @param p   the properties to read from
     * @param key the property key
     * @return the stored path, or {@link AppConfig#defaultLogFilePath()} when it cannot be used
     */
    private static Path parsePath(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return AppConfig.defaultLogFilePath();
        try {
            Path path = Path.of(v.strip());
            return path.getFileName() == null ? AppConfig.defaultLogFilePath() : path;
        } catch (InvalidPathException e) {
            return AppConfig.defaultLogFilePath();
        }
    }

    /**
     * Reads a boolean property.
     *
     * <p>Anything other than {@code "true"}, ignoring case, reads as {@code false}, which is
     * the safe answer for a switch that enables writing to disk.
     *
     * @param p        the properties to read from
     * @param key      the property key
     * @param fallback value returned when the key is missing
     * @return the parsed value, or {@code fallback}
     */
    private static boolean parseBoolean(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v.strip());
    }
}
