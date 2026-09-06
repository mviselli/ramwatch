package com.ramwatch.system;

import java.util.Objects;

/**
 * Immutable reading of a single running process at one polling cycle.
 *
 * <p>The memory reported is the process's resident set size, that is the physical memory it
 * actually occupies. A process that dies between two cycles simply stops appearing in the
 * next {@link SystemSnapshot}; snapshots are never updated in place.
 *
 * @param pid             operating-system process identifier; always positive
 * @param name            process name shown in the UI; never blank, stored stripped of
 *                        surrounding whitespace
 * @param usedMemoryBytes resident memory used by the process, in bytes; never negative
 * @author Michele Viselli
 * @since 1.0
 */
public record ProcessSnapshot(
        int pid,
        String name,
        long usedMemoryBytes
) {

    /**
     * Validates the reading and strips the surrounding whitespace from the process name.
     *
     * @param pid             process identifier
     * @param name            process name
     * @param usedMemoryBytes resident memory used, in bytes
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code pid} is not positive, if {@code name} is
     *                                  blank, or if {@code usedMemoryBytes} is negative
     */
    public ProcessSnapshot {
        Objects.requireNonNull(name, "name must not be null");

        if (pid <= 0) {
            throw new IllegalArgumentException("pid must be greater than zero");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (usedMemoryBytes < 0) {
            throw new IllegalArgumentException("usedMemoryBytes must not be negative");
        }

        name = name.strip();
    }
}
