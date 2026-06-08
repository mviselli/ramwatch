package com.ramwatch.analysis;

import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;
import com.ramwatch.system.SystemSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private static final long MB = 1024L * 1024;
    private static final long GB = 1024L * MB;

    private static SystemSnapshot snapshot(long totalBytes, long availableBytes, List<ProcessSnapshot> processes) {
        MemorySnapshot mem = MemorySnapshot.fromTotalAndAvailable(totalBytes, availableBytes, NOW);
        return SystemSnapshot.fromMemoryAndProcesses(mem, processes);
    }

    private static ProcessSnapshot process(int pid, String name, long memoryMb) {
        return new ProcessSnapshot(pid, name, memoryMb * MB);
    }

    // --- percentuali ---

    @Test
    void calculatesUsedAndFreePercent() {
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, List.of());
        AnalyzedSnapshot result = MemoryAnalyzer.withDefaults().analyze(sys);

        assertEquals(75.0, result.usedPercent());
        assertEquals(25.0, result.freePercent());
    }

    @Test
    void percentagesSumToOneHundred() {
        SystemSnapshot sys = snapshot(8 * GB, 3 * GB, List.of());
        AnalyzedSnapshot result = MemoryAnalyzer.withDefaults().analyze(sys);

        assertEquals(100.0, result.usedPercent() + result.freePercent(), 0.01);
    }

    // --- stati ---

    @Test
    void stableWhenFreeAboveWarningThreshold() {
        // 8 GB total, 2 GB free = 25% free → STABLE (threshold 20%)
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, List.of());
        assertEquals(RamState.STABLE, MemoryAnalyzer.withDefaults().analyze(sys).state());
    }

    @Test
    void warningWhenFreeBetweenCriticalAndWarningThreshold() {
        // 8 GB total, 1.2 GB free = 15% free → WARNING (10% < 15% <= 20%)
        long available = (long) (8 * GB * 0.15);
        SystemSnapshot sys = snapshot(8 * GB, available, List.of());
        assertEquals(RamState.WARNING, MemoryAnalyzer.withDefaults().analyze(sys).state());
    }

    @Test
    void criticalWhenFreeBelowCriticalThreshold() {
        // 8 GB total, 0.6 GB free = 7.5% free → CRITICAL (< 10%)
        long available = (long) (8 * GB * 0.075);
        SystemSnapshot sys = snapshot(8 * GB, available, List.of());
        assertEquals(RamState.CRITICAL, MemoryAnalyzer.withDefaults().analyze(sys).state());
    }

    @Test
    void customThresholdsAreRespected() {
        MemoryAnalyzer analyzer = new MemoryAnalyzer(30.0, 15.0, 0, 10);
        // 8 GB total, 20% free → WARNING with custom thresholds (15% < 20% <= 30%)
        long available = (long) (8 * GB * 0.20);
        SystemSnapshot sys = snapshot(8 * GB, available, List.of());
        assertEquals(RamState.WARNING, analyzer.analyze(sys).state());
    }

    // --- top consumer ---

    @Test
    void topConsumersAreSortedByMemoryDescending() {
        List<ProcessSnapshot> processes = List.of(
                process(1, "small", 200),
                process(2, "large", 800),
                process(3, "medium", 400)
        );
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, processes);
        List<ProcessSnapshot> top = MemoryAnalyzer.withDefaults().analyze(sys).topConsumers();

        assertEquals(3, top.size());
        assertEquals("large", top.get(0).name());
        assertEquals("medium", top.get(1).name());
        assertEquals("small", top.get(2).name());
    }

    @Test
    void topConsumersAreLimitedToTopN() {
        MemoryAnalyzer analyzer = new MemoryAnalyzer(20.0, 10.0, 0, 3);
        List<ProcessSnapshot> processes = List.of(
                process(1, "a", 500),
                process(2, "b", 400),
                process(3, "c", 300),
                process(4, "d", 200),
                process(5, "e", 100)
        );
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, processes);
        assertEquals(3, analyzer.analyze(sys).topConsumers().size());
    }

    // --- filtro soglia minima ---

    @Test
    void processesUnderMinThresholdAreExcluded() {
        // default min = 100 MB
        List<ProcessSnapshot> processes = List.of(
                process(1, "heavy", 500),
                process(2, "tiny", 50)  // sotto soglia
        );
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, processes);
        List<ProcessSnapshot> top = MemoryAnalyzer.withDefaults().analyze(sys).topConsumers();

        assertEquals(1, top.size());
        assertEquals("heavy", top.get(0).name());
    }

    @Test
    void zeroMinThresholdIncludesAllProcesses() {
        MemoryAnalyzer analyzer = new MemoryAnalyzer(20.0, 10.0, 0, 10);
        List<ProcessSnapshot> processes = List.of(
                process(1, "tiny", 1),
                process(2, "also-tiny", 5)
        );
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, processes);
        assertEquals(2, analyzer.analyze(sys).topConsumers().size());
    }

    // --- ricerca per nome ---

    @Test
    void filterByNameReturnsCaseInsensitiveMatches() {
        List<ProcessSnapshot> processes = List.of(
                process(1, "Chrome", 600),
                process(2, "chrome-helper", 200),
                process(3, "Safari", 300)
        );
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, processes);
        List<ProcessSnapshot> result = MemoryAnalyzer.withDefaults().filterByName(sys, "chrome");

        assertEquals(2, result.size());
        assertEquals("Chrome", result.get(0).name());
        assertEquals("chrome-helper", result.get(1).name());
    }

    @Test
    void filterByNameReturnsEmptyForBlankQuery() {
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, List.of(process(1, "Chrome", 500)));
        assertTrue(MemoryAnalyzer.withDefaults().filterByName(sys, "  ").isEmpty());
        assertTrue(MemoryAnalyzer.withDefaults().filterByName(sys, null).isEmpty());
    }

    // --- validazione costruttore ---

    @Test
    void rejectsInvalidThresholds() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryAnalyzer(10.0, 20.0, 0, 10)); // critical >= warning
    }

    @Test
    void rejectsZeroTopN() {
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryAnalyzer(20.0, 10.0, 0, 0));
    }

    @Test
    void sampledAtMatchesMemorySnapshot() {
        SystemSnapshot sys = snapshot(8 * GB, 2 * GB, List.of());
        assertEquals(NOW, MemoryAnalyzer.withDefaults().analyze(sys).sampledAt());
    }
}
