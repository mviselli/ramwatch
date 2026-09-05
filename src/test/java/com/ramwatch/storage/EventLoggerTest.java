package com.ramwatch.storage;

import com.ramwatch.analysis.RamState;
import com.ramwatch.system.ProcessSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventLoggerTest {

    private static final long GB = 1024L * 1024 * 1024;

    @TempDir
    Path tmp;

    private static MemoryEvent event(RamState state, ProcessSnapshot top) {
        return new MemoryEvent(
                Instant.parse("2026-09-05T10:11:12Z"),
                state,
                16 * GB,
                15 * GB,
                1 * GB,
                top
        );
    }

    @Test
    void log_writesOneLine_andCreatesMissingDirectories() throws IOException {
        Path file = tmp.resolve("nested").resolve("events.log");
        EventLogger logger = new EventLogger(file, true);

        assertTrue(logger.log(event(RamState.CRITICAL, new ProcessSnapshot(1234, "Chrome", 2 * GB))));

        List<String> lines = Files.readAllLines(file);
        assertEquals(1, lines.size());
        String line = lines.get(0);
        assertTrue(line.startsWith("2026-09-05T10:11:12Z | CRITICAL |"), line);
        assertTrue(line.contains("total=16.00 GB"), line);
        assertTrue(line.contains("used=15.00 GB"), line);
        assertTrue(line.contains("free=1.00 GB"), line);
        assertTrue(line.contains("top=Chrome pid=1234 mem=2.00 GB"), line);
    }

    @Test
    void log_appendsSubsequentEvents() throws IOException {
        Path file = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(file, true);

        logger.log(event(RamState.WARNING, null));
        logger.log(event(RamState.CRITICAL, null));

        assertEquals(2, Files.readAllLines(file).size());
    }

    @Test
    void log_writesNoTopConsumer_whenAbsent() throws IOException {
        Path file = tmp.resolve("events.log");
        new EventLogger(file, true).log(event(RamState.WARNING, null));

        assertTrue(Files.readString(file).contains("top=none"));
    }

    @Test
    void log_isNoOp_whenDisabled() {
        Path file = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(file, false);

        assertFalse(logger.log(event(RamState.CRITICAL, null)));
        assertFalse(Files.exists(file));
        assertTrue(logger.lastError().isEmpty());
    }

    @Test
    void setEnabled_togglesLoggingAtRuntime() {
        Path file = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(file, false);

        assertFalse(logger.log(event(RamState.WARNING, null)));
        logger.setEnabled(true);
        assertTrue(logger.log(event(RamState.WARNING, null)));
        assertTrue(Files.exists(file));
    }

    @Test
    void log_rotatesFile_whenSizeCapExceeded() throws IOException {
        Path file = tmp.resolve("events.log");
        Path rotated = tmp.resolve("events.log.1");
        String line = EventLogger.format(event(RamState.WARNING, null));
        long cap = line.length() + 1; // room for one line only
        EventLogger logger = new EventLogger(file, true, cap);

        logger.log(event(RamState.WARNING, null));
        assertFalse(Files.exists(rotated));

        logger.log(event(RamState.CRITICAL, null));

        assertTrue(Files.exists(rotated), "previous log should be kept as .1 backup");
        assertEquals(1, Files.readAllLines(rotated).size());
        List<String> current = Files.readAllLines(file);
        assertEquals(1, current.size());
        assertTrue(current.get(0).contains("CRITICAL"));
    }

    @Test
    void log_rotationKeepsSingleBackup() throws IOException {
        Path file = tmp.resolve("events.log");
        long cap = EventLogger.format(event(RamState.WARNING, null)).length() + 1;
        EventLogger logger = new EventLogger(file, true, cap);

        for (int i = 0; i < 5; i++) {
            logger.log(event(RamState.WARNING, null));
        }

        try (var entries = Files.list(tmp)) {
            assertEquals(2, entries.count(), "only the log and one backup should exist");
        }
    }

    @Test
    void log_reportsFailure_withoutThrowing() throws IOException {
        Path blocked = tmp.resolve("blocker");
        Files.writeString(blocked, "not a directory");
        EventLogger logger = new EventLogger(blocked.resolve("events.log"), true);

        assertFalse(logger.log(event(RamState.CRITICAL, null)));
        assertTrue(logger.lastError().isPresent());
    }

    @Test
    void constructor_rejectsNonPositiveMaxFileBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventLogger(tmp.resolve("events.log"), true, 0));
    }
}
