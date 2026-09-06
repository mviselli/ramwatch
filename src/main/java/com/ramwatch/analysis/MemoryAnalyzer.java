package com.ramwatch.analysis;

import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;
import com.ramwatch.system.SystemSnapshot;

import java.util.Comparator;
import java.util.List;

/**
 * Turns a raw {@link SystemSnapshot} into an {@link AnalyzedSnapshot} the UI can render as is.
 *
 * <p>One instance holds the thresholds and the filter currently in force. They are fixed for
 * the lifetime of the instance: when the user changes a setting, the application builds a new
 * analyzer rather than mutating this one, so a cycle in flight always works with a coherent
 * configuration.
 *
 * <p>Thresholds are expressed as percentages of <em>free</em> memory, so a lower number means
 * a worse situation, and the critical threshold must therefore be lower than the warning one.
 *
 * <p>Instances are immutable and safe to use from the polling thread.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class MemoryAnalyzer {

    /** Free memory percentage below which the state becomes {@link RamState#WARNING} by default. */
    private static final double DEFAULT_WARNING_FREE_PERCENT = 20.0;

    /** Free memory percentage below which the state becomes {@link RamState#CRITICAL} by default. */
    private static final double DEFAULT_CRITICAL_FREE_PERCENT = 10.0;

    /** Processes lighter than this are hidden from the table by default. */
    private static final long DEFAULT_MIN_PROCESS_BYTES = 100L * 1024 * 1024; // 100 MB

    /** Number of top consumers kept by default. */
    private static final int DEFAULT_TOP_N = 10;

    /** Free memory percentage at or below which the state is at least {@link RamState#WARNING}. */
    private final double warningFreePercent;

    /** Free memory percentage at or below which the state is {@link RamState#CRITICAL}. */
    private final double criticalFreePercent;

    /** Minimum resident memory, in bytes, for a process to be ranked. */
    private final long minProcessBytes;

    /** Maximum number of top consumers reported. */
    private final int topN;

    /**
     * Creates an analyzer with explicit thresholds and filter.
     *
     * @param warningFreePercent  free memory percentage triggering {@link RamState#WARNING};
     *                            must be between 0 and 100 exclusive
     * @param criticalFreePercent free memory percentage triggering {@link RamState#CRITICAL};
     *                            must be between 0 and 100 exclusive and strictly lower than
     *                            {@code warningFreePercent}
     * @param minProcessBytes     minimum resident memory, in bytes, for a process to be ranked;
     *                            use {@code 0} to rank every process
     * @param topN                maximum number of top consumers to report; must be positive
     * @throws IllegalArgumentException if a percentage is out of range, if the critical
     *                                  threshold is not stricter than the warning one, if
     *                                  {@code minProcessBytes} is negative or if {@code topN}
     *                                  is not positive
     */
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

    /**
     * Creates an analyzer with the built-in defaults: warning at 20% free, critical at 10%
     * free, processes under 100 MB hidden and the ten heaviest reported.
     *
     * @return a ready-to-use analyzer, never {@code null}
     */
    public static MemoryAnalyzer withDefaults() {
        return new MemoryAnalyzer(
                DEFAULT_WARNING_FREE_PERCENT,
                DEFAULT_CRITICAL_FREE_PERCENT,
                DEFAULT_MIN_PROCESS_BYTES,
                DEFAULT_TOP_N
        );
    }

    /**
     * Analyses one polling cycle: computes the percentages, resolves the state and ranks the
     * heaviest processes.
     *
     * <p>Processes lighter than the configured minimum are dropped before ranking, so the
     * filter is applied to the data, not to the display. Ties keep the order in which the
     * operating system reported them.
     *
     * @param snapshot the raw cycle to analyse; must not be {@code null}
     * @return the analysed cycle, timestamped like the memory reading it came from
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
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

    /**
     * Searches the processes of a cycle by name, ignoring case.
     *
     * <p>Independent of {@link #analyze}: the search runs on the full process list, so the
     * minimum-memory filter and the top-N cap do not apply and a small process can still be
     * found. The original snapshot is never modified.
     *
     * @param snapshot the cycle to search in; must not be {@code null}
     * @param query    substring to look for in the process name; case is ignored and
     *                 surrounding whitespace stripped
     * @return the matching processes sorted by memory descending, or an empty list when the
     *         query is {@code null} or blank
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public List<ProcessSnapshot> filterByName(SystemSnapshot snapshot, String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.strip().toLowerCase();
        return snapshot.processes().stream()
                .filter(p -> p.name().toLowerCase().contains(lower))
                .sorted(Comparator.comparingLong(ProcessSnapshot::usedMemoryBytes).reversed())
                .toList();
    }

    /**
     * Maps a free memory percentage onto a state.
     *
     * <p>Both thresholds are inclusive, so landing exactly on one already counts as the worse
     * state, and the critical check comes first.
     *
     * @param freePercent share of memory available, from 0 to 100
     * @return the matching state
     */
    private RamState determineState(double freePercent) {
        if (freePercent <= criticalFreePercent) return RamState.CRITICAL;
        if (freePercent <= warningFreePercent) return RamState.WARNING;
        return RamState.STABLE;
    }

    /**
     * Rounds a percentage to one decimal, the precision shown in the UI.
     *
     * @param value the raw percentage
     * @return the value rounded to one decimal
     */
    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
