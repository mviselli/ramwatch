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

class EventLogReaderTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final Instant AT = Instant.parse("2026-09-05T10:11:12Z");

    @TempDir
    Path tmp;

    private static MemoryEvent event(ProcessSnapshot top) {
        // Deliberately not round GB values: rereading must not lose precision.
        return new MemoryEvent(AT, RamState.CRITICAL, 17179869184L, 16106127361L, 1073741823L, top);
    }

    @Test
    void read_recoversExactByteCounts() throws IOException {
        Path log = tmp.resolve("events.log");
        MemoryEvent original = event(new ProcessSnapshot(1234, "Chrome", 2147483647L));
        new EventLogger(log, true).log(original);

        List<MemoryEvent> events = EventLogReader.read(log);

        assertEquals(1, events.size());
        assertEquals(original, events.get(0));
    }

    @Test
    void read_recoversEventsWithoutTopConsumer() throws IOException {
        Path log = tmp.resolve("events.log");
        MemoryEvent original = event(null);
        new EventLogger(log, true).log(original);

        assertEquals(original, EventLogReader.read(log).get(0));
    }

    @Test
    void read_keepsOrderAcrossMultipleEvents() throws IOException {
        Path log = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(log, true);
        logger.log(new MemoryEvent(AT, RamState.WARNING, 16 * GB, 13 * GB, 3 * GB, null));
        logger.log(new MemoryEvent(AT.plusSeconds(5), RamState.CRITICAL, 16 * GB, 15 * GB, GB, null));

        List<MemoryEvent> events = EventLogReader.read(log);

        assertEquals(RamState.WARNING, events.get(0).state());
        assertEquals(RamState.CRITICAL, events.get(1).state());
        assertEquals(AT.plusSeconds(5), events.get(1).occurredAt());
    }

    @Test
    void read_handlesProcessNamesContainingTheFieldSeparator() throws IOException {
        Path log = tmp.resolve("events.log");
        MemoryEvent original = event(new ProcessSnapshot(99, "Odd | Name pid=fake", 12345L));
        new EventLogger(log, true).log(original);

        assertEquals(original, EventLogReader.read(log).get(0));
    }

    @Test
    void read_skipsUnparsableLines() throws IOException {
        Path log = tmp.resolve("events.log");
        new EventLogger(log, true).log(event(null));
        Files.writeString(log, "garbage line\n\n", java.nio.file.StandardOpenOption.APPEND);

        assertEquals(1, EventLogReader.read(log).size());
    }

    @Test
    void read_returnsEmptyList_whenFileIsMissing() throws IOException {
        assertEquals(List.of(), EventLogReader.read(tmp.resolve("missing.log")));
    }

    @Test
    void parse_rejectsLinesWithoutRawByteField() {
        assertNull(EventLogReader.parse(
                "2026-09-05T10:11:12Z | CRITICAL | total=16.00 GB | used=15.00 GB | free=1.00 GB | top=none"));
    }
}
