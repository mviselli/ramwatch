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
 */
public final class EventLogReader {

    private static final String BYTES_MARKER = " | bytes=";
    private static final String SEPARATOR = " | ";

    private EventLogReader() {}

    /** Events in the file, oldest first; empty when the file does not exist. */
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

    /** One log line, or {@code null} when it does not parse. */
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

    /** Parses {@code top=<name> pid=<pid> mem=<formatted>}, using the raw byte count. */
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
