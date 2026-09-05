package com.ramwatch.storage;

import com.ramwatch.system.ProcessSnapshot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes logged events as CSV for spreadsheets and analysis tools.
 * Memory values stay in raw bytes so they can be summed and sorted without parsing.
 */
public final class EventCsvExporter {

    static final String HEADER =
            "timestamp,state,total_bytes,used_bytes,free_bytes,top_process,top_pid,top_bytes";

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private EventCsvExporter() {}

    /**
     * Exports the events recorded in {@code logFile} to {@code csvFile}, replacing it
     * if it already exists.
     *
     * @return how many events were written.
     */
    public static int exportLog(Path logFile, Path csvFile) throws IOException {
        List<MemoryEvent> events = EventLogReader.read(logFile);
        export(events, csvFile);
        return events.size();
    }

    public static void export(List<MemoryEvent> events, Path csvFile) throws IOException {
        Path parent = csvFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter out = Files.newBufferedWriter(
                csvFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            out.write(HEADER);
            out.newLine();
            for (MemoryEvent event : events) {
                out.write(toRow(event));
                out.newLine();
            }
        }
    }

    static String toRow(MemoryEvent event) {
        ProcessSnapshot top = event.topConsumer();
        StringBuilder sb = new StringBuilder(120);
        sb.append(TIMESTAMP.format(event.occurredAt())).append(',')
                .append(event.state()).append(',')
                .append(event.totalBytes()).append(',')
                .append(event.usedBytes()).append(',')
                .append(event.freeBytes()).append(',')
                .append(top == null ? "" : escape(top.name())).append(',')
                .append(top == null ? "" : String.valueOf(top.pid())).append(',')
                .append(top == null ? "" : String.valueOf(top.usedMemoryBytes()));
        return sb.toString();
    }

    /** Quotes a field when it carries a comma, a quote or a line break. */
    private static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
