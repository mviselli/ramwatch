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
 * Memory values stay in raw bytes so they can be summed and sorted without parsing, and
 * timestamps in ISO-8601 so they sort as text.
 *
 * <p>An event with no top consumer leaves the last three columns empty rather than writing a
 * placeholder, so a spreadsheet reads them as blanks instead of as data.
 *
 * <p>This class is stateless and cannot be instantiated.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class EventCsvExporter {

    /** Column names written as the first line of every export. */
    static final String HEADER =
            "timestamp,state,total_bytes,used_bytes,free_bytes,top_process,top_pid,top_bytes";

    /** Timestamps are written in ISO-8601 so they sort and parse unambiguously. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    /** Utility class: not meant to be instantiated. */
    private EventCsvExporter() {}

    /**
     * Exports the events recorded in {@code logFile} to {@code csvFile}, replacing it
     * if it already exists.
     *
     * <p>An empty or absent log still produces a valid file holding just the header row.
     *
     * @param logFile the log to read the events from; must not be {@code null}
     * @param csvFile the file to write; overwritten when it already exists
     * @return how many events were written.
     * @throws IOException          if the log cannot be read or the CSV cannot be written
     * @throws NullPointerException if either path is {@code null}
     */
    public static int exportLog(Path logFile, Path csvFile) throws IOException {
        List<MemoryEvent> events = EventLogReader.read(logFile);
        export(events, csvFile);
        return events.size();
    }

    /**
     * Writes the given events as CSV, creating the parent directory if needed.
     *
     * <p>An existing file is truncated, not appended to, so an export is always a complete
     * picture of what was passed in.
     *
     * @param events  the events to write, in the order they should appear; must not be {@code null}
     * @param csvFile the file to write; overwritten when it already exists
     * @throws IOException          if the directory or the file cannot be written
     * @throws NullPointerException if either argument is {@code null}
     */
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

    /**
     * Renders one event as a CSV row, without the line break.
     *
     * @param event the event to render; must not be {@code null}
     * @return the row, with the last three columns empty when no top consumer was recorded
     */
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

    /**
     * Quotes a field when it carries a comma, a quote or a line break.
     *
     * <p>Only the process name can contain such characters; embedded quotes are doubled, as
     * the CSV convention requires.
     *
     * @param value the field to escape; must not be {@code null}
     * @return the field, quoted only if it needs to be
     */
    private static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
