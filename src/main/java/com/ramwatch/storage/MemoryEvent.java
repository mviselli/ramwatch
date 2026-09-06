package com.ramwatch.storage;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A single loggable memory event: the system state at the moment a threshold
 * was crossed, plus the process consuming most memory at that time.
 *
 * <p>Events are recorded only on a state transition, never on every polling cycle, so the
 * log stays a history of what changed rather than a stream of samples. Sizes are kept in raw
 * bytes: the readable formatting belongs to the log line, not to the model.
 *
 * <p>{@code topConsumer} may be absent when no process passed the analyzer filter.
 *
 * @param occurredAt  instant the threshold was crossed, taken from the underlying reading
 * @param state       state observed at that moment
 * @param totalBytes  total physical memory, in bytes; always positive
 * @param usedBytes   memory in use, in bytes
 * @param freeBytes   memory available, in bytes
 * @param topConsumer heaviest process at that moment, or {@code null} when none passed the
 *                    analyzer filter; read it through {@link #findTopConsumer()}
 * @author Michele Viselli
 * @since 1.0
 */
public record MemoryEvent(
        Instant occurredAt,
        RamState state,
        long totalBytes,
        long usedBytes,
        long freeBytes,
        ProcessSnapshot topConsumer
) {

    /**
     * Validates the recorded values.
     *
     * @param occurredAt  instant the threshold was crossed
     * @param state       state observed
     * @param totalBytes  total physical memory, in bytes
     * @param usedBytes   memory in use, in bytes
     * @param freeBytes   memory available, in bytes
     * @param topConsumer heaviest process, possibly {@code null}
     * @throws NullPointerException     if {@code occurredAt} or {@code state} is {@code null}
     * @throws IllegalArgumentException if {@code totalBytes} is not positive, or if
     *                                  {@code usedBytes} or {@code freeBytes} is negative
     */
    public MemoryEvent {
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(state, "state must not be null");

        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes must be greater than zero");
        }
        if (usedBytes < 0 || freeBytes < 0) {
            throw new IllegalArgumentException("usedBytes and freeBytes must not be negative");
        }
    }

    /**
     * Builds an event from an analyzed snapshot, taking the heaviest process as top consumer.
     *
     * <p>The raw memory reading is needed alongside the analysis because the latter carries
     * percentages, while an event records absolute byte counts. The top consumer is the first
     * entry of the already-ranked list, so nothing is sorted again here.
     *
     * @param analyzed the analysed cycle the event is derived from; must not be {@code null}
     * @param memory   the raw memory reading of the same cycle; must not be {@code null}
     * @return the event to record, timestamped like the cycle
     * @throws NullPointerException if either argument is {@code null}
     */
    public static MemoryEvent from(AnalyzedSnapshot analyzed, MemorySnapshot memory) {
        Objects.requireNonNull(analyzed, "analyzed must not be null");
        Objects.requireNonNull(memory, "memory must not be null");

        ProcessSnapshot top = analyzed.topConsumers().isEmpty() ? null : analyzed.topConsumers().get(0);
        return new MemoryEvent(
                analyzed.sampledAt(),
                analyzed.state(),
                memory.totalBytes(),
                memory.usedBytes(),
                memory.availableBytes(),
                top
        );
    }

    /**
     * The heaviest process at the time of the event, if one was recorded.
     *
     * @return the top consumer, or an empty optional when no process passed the analyzer filter
     */
    public Optional<ProcessSnapshot> findTopConsumer() {
        return Optional.ofNullable(topConsumer);
    }
}
