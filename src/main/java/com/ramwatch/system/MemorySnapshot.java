package com.ramwatch.system;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable reading of the machine's physical memory at one instant.
 *
 * <p>All sizes are kept in bytes; formatting for display is
 * {@link MemoryFormatter}'s job. The three sizes are always consistent, because
 * {@code usedBytes} is required to equal {@code totalBytes - availableBytes}.
 *
 * <p>Prefer the {@link #fromTotalAndAvailable(long, long, Instant)} factory over the
 * canonical constructor: it derives the used amount instead of recomputing it at the
 * call site.
 *
 * @param totalBytes     total physical memory installed, in bytes; always positive
 * @param availableBytes memory available to new allocations, in bytes
 * @param usedBytes      memory currently in use, in bytes; equals {@code totalBytes - availableBytes}
 * @param sampledAt      instant the reading was taken
 * @author Michele Viselli
 * @since 1.0
 */
public record MemorySnapshot(
        long totalBytes,
        long availableBytes,
        long usedBytes,
        Instant sampledAt
) {

    /**
     * Validates that the reading is internally consistent.
     *
     * @param totalBytes     total physical memory, in bytes
     * @param availableBytes available memory, in bytes
     * @param usedBytes      used memory, in bytes
     * @param sampledAt      instant the reading was taken
     * @throws NullPointerException     if {@code sampledAt} is {@code null}
     * @throws IllegalArgumentException if {@code totalBytes} is not positive, if any other
     *                                  size is negative or larger than {@code totalBytes},
     *                                  or if {@code usedBytes} does not equal
     *                                  {@code totalBytes - availableBytes}
     */
    public MemorySnapshot {
        Objects.requireNonNull(sampledAt, "sampledAt must not be null");

        if (totalBytes <= 0) {
            throw new IllegalArgumentException("totalBytes must be greater than zero");
        }
        if (availableBytes < 0) {
            throw new IllegalArgumentException("availableBytes must not be negative");
        }
        if (usedBytes < 0) {
            throw new IllegalArgumentException("usedBytes must not be negative");
        }
        if (availableBytes > totalBytes) {
            throw new IllegalArgumentException("availableBytes must not exceed totalBytes");
        }
        if (usedBytes > totalBytes) {
            throw new IllegalArgumentException("usedBytes must not exceed totalBytes");
        }
        if (usedBytes != totalBytes - availableBytes) {
            throw new IllegalArgumentException("usedBytes must equal totalBytes - availableBytes");
        }
    }

    /**
     * Creates a snapshot deriving the used amount from the total and the available memory.
     *
     * <p>This is the preferred way to build a snapshot from a raw OS reading, which reports
     * total and available memory but not the difference between them.
     *
     * @param totalBytes     total physical memory, in bytes; must be positive
     * @param availableBytes available memory, in bytes; must not be negative nor exceed
     *                       {@code totalBytes}
     * @param sampledAt      instant the reading was taken; must not be {@code null}
     * @return a consistent snapshot whose {@code usedBytes} is
     *         {@code totalBytes - availableBytes}
     * @throws NullPointerException     if {@code sampledAt} is {@code null}
     * @throws IllegalArgumentException if the sizes fail the record's validation
     */
    public static MemorySnapshot fromTotalAndAvailable(
            long totalBytes,
            long availableBytes,
            Instant sampledAt
    ) {
        return new MemorySnapshot(
                totalBytes,
                availableBytes,
                totalBytes - availableBytes,
                sampledAt
        );
    }
}
