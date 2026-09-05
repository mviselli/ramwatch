package com.ramwatch.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void defaults_areValid() {
        AppConfig cfg = AppConfig.defaults();
        assertEquals(2, cfg.pollingIntervalSeconds());
        assertEquals(20.0, cfg.warningFreePercent());
        assertEquals(10.0, cfg.criticalFreePercent());
        assertEquals(100L * 1024 * 1024, cfg.minProcessMemoryBytes());
        assertFalse(cfg.loggingEnabled());
        assertEquals(AppConfig.defaultLogFilePath(), cfg.logFilePath());
    }

    @Test
    void defaultLogFilePath_isEventsLogUnderRamwatchHome() {
        Path path = AppConfig.defaultLogFilePath();
        assertEquals("events.log", path.getFileName().toString());
        assertEquals(".ramwatch", path.getParent().getFileName().toString());
    }

    @Test
    void builder_overridesFields() {
        AppConfig cfg = AppConfig.builder()
                .pollingIntervalSeconds(5)
                .warningFreePercent(30.0)
                .criticalFreePercent(15.0)
                .minProcessMemoryBytes(50L * 1024 * 1024)
                .loggingEnabled(true)
                .logFilePath(Path.of("/var/log/ramwatch.log"))
                .build();

        assertEquals(5, cfg.pollingIntervalSeconds());
        assertEquals(30.0, cfg.warningFreePercent());
        assertEquals(15.0, cfg.criticalFreePercent());
        assertEquals(50L * 1024 * 1024, cfg.minProcessMemoryBytes());
        assertTrue(cfg.loggingEnabled());
        assertEquals(Path.of("/var/log/ramwatch.log"), cfg.logFilePath());
    }

    @Test
    void toBuilder_preservesAllFields() {
        AppConfig original = AppConfig.defaults();
        AppConfig modified = original.toBuilder().pollingIntervalSeconds(10).build();

        assertEquals(10, modified.pollingIntervalSeconds());
        assertEquals(original.warningFreePercent(), modified.warningFreePercent());
        assertEquals(original.criticalFreePercent(), modified.criticalFreePercent());
        assertEquals(original.minProcessMemoryBytes(), modified.minProcessMemoryBytes());
        assertEquals(original.loggingEnabled(), modified.loggingEnabled());
        assertEquals(original.logFilePath(), modified.logFilePath());
    }

    @Test
    void rejectsUnusableLogFilePath() {
        assertThrows(NullPointerException.class, () ->
                AppConfig.builder().logFilePath(null).build());
        assertThrows(IllegalArgumentException.class, () ->
                AppConfig.builder().logFilePath(Path.of("/")).build());
    }

    @Test
    void rejectsPollingIntervalBelowOne() {
        assertThrows(IllegalArgumentException.class, () ->
                AppConfig.builder().pollingIntervalSeconds(0).build());
    }

    @Test
    void rejectsCriticalGreaterThanOrEqualToWarning() {
        assertThrows(IllegalArgumentException.class, () ->
                AppConfig.builder().criticalFreePercent(20.0).warningFreePercent(20.0).build());
        assertThrows(IllegalArgumentException.class, () ->
                AppConfig.builder().criticalFreePercent(25.0).warningFreePercent(20.0).build());
    }

    @Test
    void rejectsNegativeMinProcessMemory() {
        assertThrows(IllegalArgumentException.class, () ->
                AppConfig.builder().minProcessMemoryBytes(-1).build());
    }

    @Test
    void allowsZeroMinProcessMemory() {
        AppConfig cfg = AppConfig.builder().minProcessMemoryBytes(0).build();
        assertEquals(0, cfg.minProcessMemoryBytes());
    }
}
