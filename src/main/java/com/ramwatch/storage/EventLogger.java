package com.ramwatch.storage;

import com.ramwatch.system.MemoryFormatter;
import com.ramwatch.system.ProcessSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Appends {@link MemoryEvent}s to a local text log, one readable line per event.
 *
 * <p>Logging is optional: while {@link #setEnabled(boolean) disabled} every call to
 * {@link #log(MemoryEvent)} is a no-op. Write failures never propagate — {@code log}
 * reports them through its return value and {@link #lastError()} so a polling cycle
 * can never bring the app down.
 *
 * <p>The file is size-capped: once it would exceed {@code maxFileBytes} it is rotated
 * to a single {@code .1} backup, so the log can never grow without bound.
 */
public final class EventLogger {

    static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024; // 1 MB
    private static final String ROTATED_SUFFIX = ".1";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private final Path logFile;
    private final long maxFileBytes;

    private volatile boolean enabled;
    private volatile IOException lastError;

    public EventLogger(Path logFile, boolean enabled) {
        this(logFile, enabled, DEFAULT_MAX_FILE_BYTES);
    }

    public EventLogger(Path logFile, boolean enabled, long maxFileBytes) {
        this.logFile = Objects.requireNonNull(logFile, "logFile must not be null");
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be greater than zero");
        }
        this.enabled = enabled;
        this.maxFileBytes = maxFileBytes;
    }

    public Path logFile() {
        return logFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The last write failure, if any; cleared by the next successful write. */
    public Optional<IOException> lastError() {
        return Optional.ofNullable(lastError);
    }

    /**
     * Appends one event to the log.
     *
     * @return {@code true} if the event was written, {@code false} if logging is
     *         disabled or the write failed (see {@link #lastError()}).
     */
    public boolean log(MemoryEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!enabled) {
            return false;
        }
        try {
            String line = format(event);
            rotateIfNeeded(line.getBytes(StandardCharsets.UTF_8).length);
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    logFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            lastError = null;
            return true;
        } catch (IOException e) {
            lastError = e;
            return false;
        }
    }

    private void rotateIfNeeded(int incomingBytes) throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) + incomingBytes <= maxFileBytes) {
            return;
        }
        Path rotated = logFile.resolveSibling(logFile.getFileName() + ROTATED_SUFFIX);
        Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING);
    }

    /** One event, one line: pipe-separated fields that stay readable and easy to convert. */
    static String format(MemoryEvent event) {
        StringBuilder sb = new StringBuilder(160);
        sb.append(TIMESTAMP.format(event.occurredAt()))
                .append(" | ").append(event.state())
                .append(" | total=").append(MemoryFormatter.formatBytes(event.totalBytes()))
                .append(" | used=").append(MemoryFormatter.formatBytes(event.usedBytes()))
                .append(" | free=").append(MemoryFormatter.formatBytes(event.freeBytes()))
                .append(" | top=");

        ProcessSnapshot top = event.topConsumer();
        if (top == null) {
            sb.append("none");
        } else {
            sb.append(top.name())
                    .append(" pid=").append(top.pid())
                    .append(" mem=").append(MemoryFormatter.formatBytes(top.usedMemoryBytes()));
        }
        return sb.append(System.lineSeparator()).toString();
    }
}
