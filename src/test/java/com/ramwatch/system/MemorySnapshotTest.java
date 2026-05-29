package com.ramwatch.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemorySnapshotTest {

    private static final Instant SAMPLE_TIME = Instant.parse("2026-05-29T10:15:30Z");

    @Test
    void createsSnapshotFromTotalAndAvailableBytes() {
        MemorySnapshot snapshot = MemorySnapshot.fromTotalAndAvailable(
                8_589_934_592L,
                2_147_483_648L,
                SAMPLE_TIME
        );

        assertEquals(8_589_934_592L, snapshot.totalBytes());
        assertEquals(2_147_483_648L, snapshot.availableBytes());
        assertEquals(6_442_450_944L, snapshot.usedBytes());
        assertEquals(SAMPLE_TIME, snapshot.sampledAt());
    }

    @Test
    void rejectsNonPositiveTotalMemory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MemorySnapshot.fromTotalAndAvailable(0, 0, SAMPLE_TIME)
        );
    }

    @Test
    void rejectsAvailableMemoryGreaterThanTotalMemory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MemorySnapshot.fromTotalAndAvailable(1024, 2048, SAMPLE_TIME)
        );
    }

    @Test
    void rejectsInconsistentUsedMemory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemorySnapshot(1024, 256, 512, SAMPLE_TIME)
        );
    }

    @Test
    void rejectsMissingTimestamp() {
        assertThrows(
                NullPointerException.class,
                () -> MemorySnapshot.fromTotalAndAvailable(1024, 256, null)
        );
    }
}
