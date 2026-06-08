package com.ramwatch.analysis;

import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;
import com.ramwatch.system.SystemSnapshot;

import java.util.Comparator;
import java.util.List;

public final class MemoryAnalyzer {

    private static final double DEFAULT_WARNING_FREE_PERCENT = 20.0;
    private static final double DEFAULT_CRITICAL_FREE_PERCENT = 10.0;
    private static final long DEFAULT_MIN_PROCESS_BYTES = 100L * 1024 * 1024; // 100 MB
    private static final int DEFAULT_TOP_N = 10;

    private final double warningFreePercent;
    private final double criticalFreePercent;
    private final long minProcessBytes;
    private final int topN;

    public MemoryAnalyzer(double warningFreePercent, double criticalFreePercent, long minProcessBytes, int topN) {
        if (criticalFreePercent >= warningFreePercent) {
            throw new IllegalArgumentException("criticalFreePercent must be lower than warningFreePercent");
        }
        if (warningFreePercent <= 0 || warningFreePercent >= 100) {
            throw new IllegalArgumentException("warningFreePercent must be between 0 and 100 exclusive");
        }
        if (criticalFreePercent <= 0 || criticalFreePercent >= 100) {
            throw new IllegalArgumentException("criticalFreePercent must be between 0 and 100 exclusive");
        }
        if (minProcessBytes < 0) throw new IllegalArgumentException("minProcessBytes must not be negative");
        if (topN <= 0) throw new IllegalArgumentException("topN must be greater than zero");

        this.warningFreePercent = warningFreePercent;
        this.criticalFreePercent = criticalFreePercent;
        this.minProcessBytes = minProcessBytes;
        this.topN = topN;
    }

    public static MemoryAnalyzer withDefaults() {
        return new MemoryAnalyzer(
                DEFAULT_WARNING_FREE_PERCENT,
                DEFAULT_CRITICAL_FREE_PERCENT,
                DEFAULT_MIN_PROCESS_BYTES,
                DEFAULT_TOP_N
        );
    }

    public AnalyzedSnapshot analyze(SystemSnapshot snapshot) {
        MemorySnapshot mem = snapshot.memory();

        double usedPercent = round1((double) mem.usedBytes() / mem.totalBytes() * 100.0);
        double freePercent = round1((double) mem.availableBytes() / mem.totalBytes() * 100.0);

        RamState state = determineState(freePercent);

        List<ProcessSnapshot> topConsumers = snapshot.processes().stream()
                .filter(p -> p.usedMemoryBytes() >= minProcessBytes)
                .sorted(Comparator.comparingLong(ProcessSnapshot::usedMemoryBytes).reversed())
                .limit(topN)
                .toList();

        return new AnalyzedSnapshot(usedPercent, freePercent, topConsumers, state, mem.sampledAt());
    }

    public List<ProcessSnapshot> filterByName(SystemSnapshot snapshot, String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.strip().toLowerCase();
        return snapshot.processes().stream()
                .filter(p -> p.name().toLowerCase().contains(lower))
                .sorted(Comparator.comparingLong(ProcessSnapshot::usedMemoryBytes).reversed())
                .toList();
    }

    private RamState determineState(double freePercent) {
        if (freePercent <= criticalFreePercent) return RamState.CRITICAL;
        if (freePercent <= warningFreePercent) return RamState.WARNING;
        return RamState.STABLE;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
