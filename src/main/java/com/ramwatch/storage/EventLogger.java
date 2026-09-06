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
 *
 * <p>The enabled flag and the last error are {@code volatile}: the polling thread writes
 * them while the JavaFX thread reads them to show the warning banner.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class EventLogger {

    /** Size at which the log is rotated, unless the caller chooses another one. */
    static final long DEFAULT_MAX_FILE_BYTES = 1024L * 1024; // 1 MB

    /** Suffix of the single backup kept on rotation. */
    private static final String ROTATED_SUFFIX = ".1";

    /** Timestamps are written in ISO-8601 so they sort and parse unambiguously. */
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    /** File events are appended to; created on the first successful write. */
    private final Path logFile;

    /** Size, in bytes, the log may not exceed. */
    private final long maxFileBytes;

    /** Whether writing is currently on; toggled from the settings screen. */
    private volatile boolean enabled;

    /** Last write failure, or {@code null} when the last write succeeded. */
    private volatile IOException lastError;

    /**
     * Creates a logger with the default 1 MB rotation size.
     *
     * @param logFile file to append to; must not be {@code null}
     * @param enabled whether writing starts on
     * @throws NullPointerException if {@code logFile} is {@code null}
     */
    public EventLogger(Path logFile, boolean enabled) {
        this(logFile, enabled, DEFAULT_MAX_FILE_BYTES);
    }

    /**
     * Creates a logger with an explicit rotation size.
     *
     * <p>Nothing is written or created here: the file appears on the first event logged.
     *
     * @param logFile      file to append to; must not be {@code null}
     * @param enabled      whether writing starts on
     * @param maxFileBytes size in bytes the log may not exceed; must be positive
     * @throws NullPointerException     if {@code logFile} is {@code null}
     * @throws IllegalArgumentException if {@code maxFileBytes} is not positive
     */
    public EventLogger(Path logFile, boolean enabled, long maxFileBytes) {
        this.logFile = Objects.requireNonNull(logFile, "logFile must not be null");
        if (maxFileBytes <= 0) {
            throw new IllegalArgumentException("maxFileBytes must be greater than zero");
        }
        this.enabled = enabled;
        this.maxFileBytes = maxFileBytes;
    }

    /**
     * The file this logger writes to.
     *
     * @return the log file path, as given at construction
     */
    public Path logFile() {
        return logFile;
    }

    /**
     * Whether events are currently being written.
     *
     * @return {@code true} if logging is on
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns logging on or off, taking effect from the next event.
     *
     * @param enabled {@code true} to start writing, {@code false} to make {@link #log} a no-op
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The last write failure, if any; cleared by the next successful write.
     *
     * <p>This is how a failure surfaces at all, since {@link #log} never throws: the UI polls
     * it to show the warning banner.
     *
     * @return the last failure, or an empty optional when the last write succeeded
     */
    public Optional<IOException> lastError() {
        return Optional.ofNullable(lastError);
    }

    /**
     * Appends one event to the log.
     *
     * <p>Rotation is checked first, and the parent directory created if missing. An I/O
     * failure is captured instead of thrown: this runs on the polling thread, where an
     * exception would kill the scheduled cycle.
     *
     * @param event the event to record; must not be {@code null}
     * @return {@code true} if the event was written, {@code false} if logging is
     *         disabled or the write failed (see {@link #lastError()}).
     * @throws NullPointerException if {@code event} is {@code null}
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

    /**
     * Moves the log aside when the incoming line would push it past the size cap.
     *
     * <p>Only one backup is kept: an existing {@code .1} file is replaced, so the log costs
     * at most twice the cap on disk.
     *
     * @param incomingBytes size of the line about to be appended, in bytes
     * @throws IOException if the existing log cannot be measured or moved
     */
    private void rotateIfNeeded(int incomingBytes) throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) + incomingBytes <= maxFileBytes) {
            return;
        }
        Path rotated = logFile.resolveSibling(logFile.getFileName() + ROTATED_SUFFIX);
        Files.move(logFile, rotated, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * One event, one line: pipe-separated fields that stay readable, closing with the
     * raw byte counts so {@link EventLogReader} can recover exact values.
     *
     * <p>A missing top consumer is written as {@code top=none} and as a {@code -} in the raw
     * field, so the line keeps the same shape whether or not a process was recorded.
     *
     * @param event the event to render; must not be {@code null}
     * @return the line to append, line separator included
     */
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

        sb.append(" | bytes=").append(event.totalBytes())
                .append(',').append(event.usedBytes())
                .append(',').append(event.freeBytes())
                .append(',').append(top == null ? "-" : String.valueOf(top.usedMemoryBytes()));

        return sb.append(System.lineSeparator()).toString();
    }
}
