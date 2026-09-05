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
 * Falls back to defaults on any read error or missing file.
 */
public final class ConfigStore {

    static final String KEY_POLLING_INTERVAL = "polling.intervalSeconds";
    static final String KEY_WARNING_FREE_PERCENT = "threshold.warningFreePercent";
    static final String KEY_CRITICAL_FREE_PERCENT = "threshold.criticalFreePercent";
    static final String KEY_MIN_PROCESS_MEMORY_MB = "process.minMemoryMb";
    static final String KEY_LOGGING_ENABLED = "logging.enabled";
    static final String KEY_LOG_FILE_PATH = "logging.filePath";

    private final Path configFile;

    public ConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    public static ConfigStore atDefaultLocation() {
        Path home = Path.of(System.getProperty("user.home"));
        return new ConfigStore(home.resolve(".ramwatch").resolve("config.properties"));
    }

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

    private static int parseInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.strip()); } catch (NumberFormatException e) { return fallback; }
    }

    private static long parseLongMb(Properties p, String key, long fallbackMb) {
        String v = p.getProperty(key);
        if (v == null) return fallbackMb * 1024 * 1024;
        try { return Long.parseLong(v.strip()) * 1024 * 1024; } catch (NumberFormatException e) { return fallbackMb * 1024 * 1024; }
    }

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

    private static boolean parseBoolean(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v.strip());
    }
}
