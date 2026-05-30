package com.ramwatch.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProcessSnapshotTest {

    @Test
    void createsProcessSnapshot() {
        ProcessSnapshot snapshot = new ProcessSnapshot(
                1234,
                "java",
                268_435_456L
        );

        assertEquals(1234, snapshot.pid());
        assertEquals("java", snapshot.name());
        assertEquals(268_435_456L, snapshot.usedMemoryBytes());
    }

    @Test
    void trimsProcessName() {
        ProcessSnapshot snapshot = new ProcessSnapshot(
                1234,
                "  java  ",
                1024L
        );

        assertEquals("java", snapshot.name());
    }

    @Test
    void rejectsNonPositivePid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessSnapshot(0, "java", 1024L)
        );
    }

    @Test
    void rejectsMissingName() {
        assertThrows(
                NullPointerException.class,
                () -> new ProcessSnapshot(1234, null, 1024L)
        );
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessSnapshot(1234, "   ", 1024L)
        );
    }

    @Test
    void rejectsNegativeUsedMemory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessSnapshot(1234, "java", -1L)
        );
    }
}
