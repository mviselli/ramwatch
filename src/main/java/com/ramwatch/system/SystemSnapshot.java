package com.ramwatch.system;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything captured in one polling cycle: the memory reading plus the running processes.
 *
 * <p>This is the central aggregate handed from the sampling layer to the analysis layer,
 * and from there to the UI and the event log. The process list is defensively copied, so
 * the record stays immutable whatever the caller does with the list it passed in.
 *
 * <p>Build it with {@link #fromMemoryAndProcesses(MemorySnapshot, List)} so that the
 * snapshot's timestamp is the one of the memory reading it contains.
 *
 * @param memory    the memory reading of this cycle
 * @param processes the processes seen in this cycle, in no particular order; unmodifiable
 * @param sampledAt instant the cycle was taken
 * @author Michele Viselli
 * @since 1.0
 */
public record SystemSnapshot(
        MemorySnapshot memory,
        List<ProcessSnapshot> processes,
        Instant sampledAt
) {

    /**
     * Validates the components and takes an unmodifiable copy of the process list.
     *
     * @param memory    the memory reading
     * @param processes the processes seen in this cycle
     * @param sampledAt instant the cycle was taken
     * @throws NullPointerException if any component is {@code null}, or if the process list
     *                              contains a {@code null} element
     */
    public SystemSnapshot {
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(processes, "processes must not be null");
        Objects.requireNonNull(sampledAt, "sampledAt must not be null");

        processes = List.copyOf(processes);
    }

    /**
     * Creates a snapshot reusing the memory reading's own timestamp.
     *
     * <p>This keeps a single instant for the whole cycle instead of taking a second, slightly
     * later reading of the clock.
     *
     * @param memory    the memory reading of this cycle; must not be {@code null}
     * @param processes the processes seen in this cycle; must not be {@code null}
     * @return a snapshot timestamped at {@code memory.sampledAt()}
     * @throws NullPointerException if {@code memory} or {@code processes} is {@code null}
     */
    public static SystemSnapshot fromMemoryAndProcesses(
            MemorySnapshot memory,
            List<ProcessSnapshot> processes
    ) {
        return new SystemSnapshot(memory, processes, memory.sampledAt());
    }
}
