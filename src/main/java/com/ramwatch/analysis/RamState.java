package com.ramwatch.analysis;

/**
 * How healthy the machine's memory is, derived from the percentage of free RAM.
 *
 * <p>The state drives the dashboard colours, the alert banner and the decision to record a
 * {@link com.ramwatch.storage.MemoryEvent}. It is computed on every cycle by
 * {@link MemoryAnalyzer} against the user's thresholds, while {@link StateTracker} decides
 * when a change is worth reacting to.
 *
 * <p>The constants are ordered from healthiest to worst.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public enum RamState {

    /** Free memory is above the warning threshold: normal operation, no alert. */
    STABLE,

    /** Free memory dropped to or below the warning threshold, but is still above the critical one. */
    WARNING,

    /** Free memory dropped to or below the critical threshold: the machine is under memory pressure. */
    CRITICAL
}
