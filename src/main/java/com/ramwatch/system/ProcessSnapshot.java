package com.ramwatch.system;

import java.util.Objects;

public record ProcessSnapshot(
        int pid,
        String name,
        long usedMemoryBytes
) {

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
