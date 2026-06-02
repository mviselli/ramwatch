package com.ramwatch.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemSnapshotTest {

    private static final Instant SAMPLE_TIME = Instant.parse("2026-05-29T10:15:30Z");

    @Test
    void createsSystemSnapshotFromMemoryAndProcesses() {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(4096L, 1024L, SAMPLE_TIME);
        List<ProcessSnapshot> processes = List.of(
                new ProcessSnapshot(100, "java", 512L),
                new ProcessSnapshot(200, "Finder", 256L)
        );

        SystemSnapshot snapshot = SystemSnapshot.fromMemoryAndProcesses(memory, processes);

        assertEquals(memory, snapshot.memory());
        assertEquals(processes, snapshot.processes());
        assertEquals(SAMPLE_TIME, snapshot.sampledAt());
    }

    @Test
    void copiesProcessListDefensively() {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(4096L, 1024L, SAMPLE_TIME);
        List<ProcessSnapshot> processes = new ArrayList<>();
        processes.add(new ProcessSnapshot(100, "java", 512L));

        SystemSnapshot snapshot = SystemSnapshot.fromMemoryAndProcesses(memory, processes);
        processes.add(new ProcessSnapshot(200, "Finder", 256L));

        assertEquals(1, snapshot.processes().size());
    }

    @Test
    void exposesUnmodifiableProcessList() {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(4096L, 1024L, SAMPLE_TIME);
        SystemSnapshot snapshot = SystemSnapshot.fromMemoryAndProcesses(
                memory,
                List.of(new ProcessSnapshot(100, "java", 512L))
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.processes().add(new ProcessSnapshot(200, "Finder", 256L))
        );
    }

    @Test
    void rejectsMissingMemory() {
        assertThrows(
                NullPointerException.class,
                () -> new SystemSnapshot(null, List.of(), SAMPLE_TIME)
        );
    }

    @Test
    void rejectsMissingProcessList() {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(4096L, 1024L, SAMPLE_TIME);

        assertThrows(
                NullPointerException.class,
                () -> new SystemSnapshot(memory, null, SAMPLE_TIME)
        );
    }

    @Test
    void rejectsMissingTimestamp() {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(4096L, 1024L, SAMPLE_TIME);

        assertThrows(
                NullPointerException.class,
                () -> new SystemSnapshot(memory, List.of(), null)
        );
    }
}
