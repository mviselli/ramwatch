package com.ramwatch.system;

import java.time.Instant;
import java.util.Objects;

public record MemorySnapshot(
        long totalBytes,
        long availableBytes,
        long usedBytes,
        Instant sampledAt
) {

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
