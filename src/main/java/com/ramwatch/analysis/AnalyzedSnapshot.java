package com.ramwatch.analysis;

import com.ramwatch.system.ProcessSnapshot;

import java.time.Instant;
import java.util.List;

public record AnalyzedSnapshot(
        double usedPercent,
        double freePercent,
        List<ProcessSnapshot> topConsumers,
        RamState state,
        Instant sampledAt
) {
    public AnalyzedSnapshot {
        if (usedPercent < 0 || usedPercent > 100) throw new IllegalArgumentException("usedPercent out of range: " + usedPercent);
        if (freePercent < 0 || freePercent > 100) throw new IllegalArgumentException("freePercent out of range: " + freePercent);
        if (topConsumers == null) throw new IllegalArgumentException("topConsumers must not be null");
        if (state == null) throw new IllegalArgumentException("state must not be null");
        if (sampledAt == null) throw new IllegalArgumentException("sampledAt must not be null");
        topConsumers = List.copyOf(topConsumers);
    }
}
