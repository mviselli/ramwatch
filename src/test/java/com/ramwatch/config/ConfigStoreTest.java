package com.ramwatch.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {

    @TempDir
    Path tmp;

    @Test
    void load_returnDefaults_whenFileAbsent() {
        ConfigStore store = new ConfigStore(tmp.resolve("missing.properties"));
        AppConfig cfg = store.load();
        assertEquals(AppConfig.defaults(), cfg);
    }

    @Test
    void saveAndLoad_roundtrip() throws IOException {
        Path file = tmp.resolve("config.properties");
        ConfigStore store = new ConfigStore(file);

        AppConfig original = AppConfig.builder()
                .pollingIntervalSeconds(5)
                .warningFreePercent(25.0)
                .criticalFreePercent(12.0)
                .minProcessMemoryBytes(200L * 1024 * 1024)
                .loggingEnabled(true)
                .build();

        store.save(original);
        assertTrue(Files.exists(file));

        AppConfig loaded = store.load();
        assertEquals(original, loaded);
    }

    @Test
    void load_returnDefaults_whenFileCorrupted() throws IOException {
        Path file = tmp.resolve("config.properties");
        Files.writeString(file, "not=valid\npolling.intervalSeconds=abc\n");
        ConfigStore store = new ConfigStore(file);

        AppConfig cfg = store.load();
        assertEquals(AppConfig.defaults(), cfg);
    }

    @Test
    void load_usesPerFieldFallback_forMissingKeys() throws IOException {
        Path file = tmp.resolve("config.properties");
        Files.writeString(file, ConfigStore.KEY_POLLING_INTERVAL + "=3\n");
        ConfigStore store = new ConfigStore(file);

        AppConfig cfg = store.load();
        assertEquals(3, cfg.pollingIntervalSeconds());
        assertEquals(AppConfig.defaults().warningFreePercent(), cfg.warningFreePercent());
        assertEquals(AppConfig.defaults().criticalFreePercent(), cfg.criticalFreePercent());
    }

    @Test
    void save_createsParentDirectories() throws IOException {
        Path nested = tmp.resolve("a").resolve("b").resolve("config.properties");
        ConfigStore store = new ConfigStore(nested);
        store.save(AppConfig.defaults());
        assertTrue(Files.exists(nested));
    }
}
