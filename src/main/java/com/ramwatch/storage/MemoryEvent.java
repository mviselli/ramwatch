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
 * <p>{@code topConsumer} may be absent when no process passed the analyzer filter.
 */
public record MemoryEvent(
        Instant occurredAt,
        RamState state,
        long totalBytes,
        long usedBytes,
        long freeBytes,
        ProcessSnapshot topConsumer
) {

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

    /** Builds an event from an analyzed snapshot, taking the heaviest process as top consumer. */
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

    public Optional<ProcessSnapshot> findTopConsumer() {
        return Optional.ofNullable(topConsumer);
    }
}
