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

class EventCsvExporterTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final Instant AT = Instant.parse("2026-09-05T10:11:12Z");

    @TempDir
    Path tmp;

    private static MemoryEvent event(RamState state, ProcessSnapshot top) {
        return new MemoryEvent(AT, state, 16 * GB, 15 * GB, 1 * GB, top);
    }

    @Test
    void export_writesHeaderAndRawByteValues() throws IOException {
        Path csv = tmp.resolve("events.csv");

        EventCsvExporter.export(
                List.of(event(RamState.CRITICAL, new ProcessSnapshot(1234, "Chrome", 2 * GB))), csv);

        List<String> lines = Files.readAllLines(csv);
        assertEquals(2, lines.size());
        assertEquals(EventCsvExporter.HEADER, lines.get(0));
        assertEquals("2026-09-05T10:11:12Z,CRITICAL,17179869184,16106127360,1073741824,Chrome,1234,2147483648",
                lines.get(1));
    }

    @Test
    void export_leavesTopConsumerColumnsEmpty_whenAbsent() throws IOException {
        Path csv = tmp.resolve("events.csv");

        EventCsvExporter.export(List.of(event(RamState.WARNING, null)), csv);

        assertTrue(Files.readAllLines(csv).get(1).endsWith(",,,"),
                Files.readAllLines(csv).get(1));
    }

    @Test
    void export_quotesProcessNamesContainingCommasOrQuotes() {
        String row = EventCsvExporter.toRow(
                event(RamState.CRITICAL, new ProcessSnapshot(7, "Weird, \"App\"", GB)));

        assertTrue(row.contains("\"Weird, \"\"App\"\"\""), row);
    }

    @Test
    void export_replacesAnExistingFile() throws IOException {
        Path csv = tmp.resolve("events.csv");
        Files.writeString(csv, "stale content that must not survive\n".repeat(20));

        EventCsvExporter.export(List.of(event(RamState.WARNING, null)), csv);

        List<String> lines = Files.readAllLines(csv);
        assertEquals(2, lines.size());
        assertFalse(Files.readString(csv).contains("stale"));
    }

    @Test
    void export_writesHeaderOnly_whenThereAreNoEvents() throws IOException {
        Path csv = tmp.resolve("nested").resolve("events.csv");

        EventCsvExporter.export(List.of(), csv);

        assertEquals(List.of(EventCsvExporter.HEADER), Files.readAllLines(csv));
    }

    @Test
    void exportLog_convertsWhatTheLoggerWrote() throws IOException {
        Path log = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(log, true);
        logger.log(event(RamState.WARNING, new ProcessSnapshot(1234, "Chrome", 2 * GB)));
        logger.log(event(RamState.CRITICAL, null));

        Path csv = tmp.resolve("events.csv");
        assertEquals(2, EventCsvExporter.exportLog(log, csv));

        List<String> lines = Files.readAllLines(csv);
        assertEquals(3, lines.size());
        assertTrue(lines.get(1).contains(",WARNING,"), lines.get(1));
        assertTrue(lines.get(1).endsWith(",Chrome,1234,2147483648"), lines.get(1));
        assertTrue(lines.get(2).contains(",CRITICAL,"), lines.get(2));
    }

    @Test
    void exportLog_writesEmptyCsv_whenNoLogFileExists() throws IOException {
        Path csv = tmp.resolve("events.csv");

        assertEquals(0, EventCsvExporter.exportLog(tmp.resolve("missing.log"), csv));
        assertEquals(List.of(EventCsvExporter.HEADER), Files.readAllLines(csv));
    }
}
