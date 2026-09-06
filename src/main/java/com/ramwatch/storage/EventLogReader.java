package com.ramwatch.storage;

import com.ramwatch.analysis.RamState;
import com.ramwatch.system.ProcessSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads back the lines {@link EventLogger} wrote. Exact byte counts come from the
 * trailing {@code bytes=} field, so nothing is lost to the human-readable formatting.
 *
 * <p>Unparsable lines are skipped rather than failing the whole read: a truncated or
 * hand-edited log should still export whatever it holds.
 *
 * <p>This class is stateless and cannot be instantiated.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class EventLogReader {

    /** Marker introducing the trailing field holding the exact byte counts. */
    private static final String BYTES_MARKER = " | bytes=";

    /** Separator between the readable fields of a log line. */
    private static final String SEPARATOR = " | ";

    /** Utility class: not meant to be instantiated. */
    private EventLogReader() {}

    /**
     * Reads every event the log holds.
     *
     * <p>A file that does not exist is not an error: it simply means nothing was ever logged.
     *
     * @param logFile the log file to read; must not be {@code null}
     * @return events in the file, oldest first; empty when the file does not exist or holds
     *         no parsable line
     * @throws IOException          if the file exists but cannot be read
     * @throws NullPointerException if {@code logFile} is {@code null}
     */
    public static List<MemoryEvent> read(Path logFile) throws IOException {
        if (!Files.exists(logFile)) {
            return List.of();
        }
        List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        List<MemoryEvent> events = new ArrayList<>(lines.size());
        for (String line : lines) {
            MemoryEvent event = parse(line);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    /**
     * Parses one log line.
     *
     * <p>Only the head of the line is split, and at most into six fields, so a process name
     * containing the separator cannot shift the others. Exact sizes are taken from the
     * trailing raw field rather than from the formatted ones, which are rounded.
     *
     * @param line the line to parse; may be {@code null} or blank
     * @return the event, or {@code null} when the line does not parse
     */
    static MemoryEvent parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int bytesAt = line.lastIndexOf(BYTES_MARKER);
        if (bytesAt < 0) {
            return null;
        }
        // Split only the head, so a process name containing " | " cannot break the fields.
        String[] head = line.substring(0, bytesAt).split(java.util.regex.Pattern.quote(SEPARATOR), 6);
        if (head.length < 6) {
            return null;
        }
        String[] raw = line.substring(bytesAt + BYTES_MARKER.length()).split(",", -1);
        if (raw.length != 4) {
            return null;
        }

        try {
            Instant occurredAt = Instant.parse(head[0].strip());
            RamState state = RamState.valueOf(head[1].strip());
            long total = Long.parseLong(raw[0].strip());
            long used = Long.parseLong(raw[1].strip());
            long free = Long.parseLong(raw[2].strip());

            return new MemoryEvent(occurredAt, state, total, used, free,
                    parseTopConsumer(head[5], raw[3].strip()));
        } catch (DateTimeParseException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses {@code top=<name> pid=<pid> mem=<formatted>}, using the raw byte count.
     *
     * <p>The formatted {@code mem=} value is ignored on purpose: it is rounded for display,
     * while {@code rawBytes} carries the exact figure.
     *
     * @param field    the {@code top=} field of the line
     * @param rawBytes the process's exact memory from the trailing raw field, or {@code "-"}
     *                 when no process was recorded
     * @return the top consumer, or {@code null} when none was recorded or the field does not parse
     */
    private static ProcessSnapshot parseTopConsumer(String field, String rawBytes) {
        String value = field.strip();
        if (!value.startsWith("top=")) {
            return null;
        }
        value = value.substring("top=".length());
        if (value.equals("none") || rawBytes.equals("-")) {
            return null;
        }

        int pidAt = value.lastIndexOf(" pid=");
        int memAt = value.lastIndexOf(" mem=");
        if (pidAt < 0 || memAt < pidAt) {
            return null;
        }
        String name = value.substring(0, pidAt);
        int pid = Integer.parseInt(value.substring(pidAt + " pid=".length(), memAt).strip());
        return new ProcessSnapshot(pid, name, Long.parseLong(rawBytes));
    }
}
