package com.ramwatch.analysis;

import com.ramwatch.system.ProcessSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * A polling cycle after analysis: the numbers the UI displays and the log records.
 *
 * <p>Produced by {@link MemoryAnalyzer#analyze}, it carries everything already computed —
 * percentages rounded for display, processes ranked and filtered, state resolved — so that
 * neither the UI nor the storage layer has to sort or classify anything again.
 *
 * <p>The list of top consumers is defensively copied, keeping the record immutable.
 *
 * @param usedPercent  share of physical memory in use, from 0 to 100, rounded to one decimal
 * @param freePercent  share of physical memory available, from 0 to 100, rounded to one decimal
 * @param topConsumers the heaviest processes above the minimum-memory filter, sorted by
 *                     memory descending and capped at the configured limit; unmodifiable
 *                     and possibly empty
 * @param state        the state derived from {@code freePercent} and the configured thresholds
 * @param sampledAt    instant of the underlying reading, reused from the memory snapshot
 * @author Michele Viselli
 * @since 1.0
 */
public record AnalyzedSnapshot(
        double usedPercent,
        double freePercent,
        List<ProcessSnapshot> topConsumers,
        RamState state,
        Instant sampledAt
) {
    /**
     * Validates the components and takes an unmodifiable copy of the process list.
     *
     * @param usedPercent  share of memory in use, from 0 to 100
     * @param freePercent  share of memory available, from 0 to 100
     * @param topConsumers the ranked processes
     * @param state        the derived state
     * @param sampledAt    instant of the underlying reading
     * @throws IllegalArgumentException if a percentage falls outside 0 to 100, or if any
     *                                  reference component is {@code null}
     */
    public AnalyzedSnapshot {
        if (usedPercent < 0 || usedPercent > 100) throw new IllegalArgumentException("usedPercent out of range: " + usedPercent);
        if (freePercent < 0 || freePercent > 100) throw new IllegalArgumentException("freePercent out of range: " + freePercent);
        if (topConsumers == null) throw new IllegalArgumentException("topConsumers must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (sampledAt == null) throw new IllegalArgumentException("sampledAt must not be null");
        topConsumers = List.copyOf(topConsumers);
    }
}
