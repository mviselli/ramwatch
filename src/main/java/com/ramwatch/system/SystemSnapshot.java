package com.ramwatch.system;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SystemSnapshot(
        MemorySnapshot memory,
        List<ProcessSnapshot> processes,
        Instant sampledAt
) {

    public SystemSnapshot {
        Objects.requireNonNull(memory, "memory must not be null");
        Objects.requireNonNull(processes, "processes must not be null");
        Objects.requireNonNull(sampledAt, "sampledAt must not be null");

        processes = List.copyOf(processes);
    }

    public static SystemSnapshot fromMemoryAndProcesses(
            MemorySnapshot memory,
            List<ProcessSnapshot> processes
    ) {
        return new SystemSnapshot(memory, processes, memory.sampledAt());
    }
}
