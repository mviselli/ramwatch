package com.ramwatch.storage;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.RamState;
import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryEventTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final Instant SAMPLED_AT = Instant.parse("2026-09-05T10:11:12Z");

    @Test
    void from_copiesMemoryValuesAndHeaviestProcess() {
        ProcessSnapshot heaviest = new ProcessSnapshot(1234, "Chrome", 3 * GB);
        AnalyzedSnapshot analyzed = new AnalyzedSnapshot(
                93.75, 6.25,
                List.of(heaviest, new ProcessSnapshot(22, "IDE", 1 * GB)),
                RamState.CRITICAL,
                SAMPLED_AT
        );
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(16 * GB, 1 * GB, SAMPLED_AT);

        MemoryEvent event = MemoryEvent.from(analyzed, memory);

        assertEquals(SAMPLED_AT, event.occurredAt());
        assertEquals(RamState.CRITICAL, event.state());
        assertEquals(16 * GB, event.totalBytes());
        assertEquals(15 * GB, event.usedBytes());
        assertEquals(1 * GB, event.freeBytes());
        assertEquals(heaviest, event.findTopConsumer().orElseThrow());
    }

    @Test
    void from_leavesTopConsumerAbsent_whenNoProcessSurvivedFiltering() {
        AnalyzedSnapshot analyzed = new AnalyzedSnapshot(
                50.0, 50.0, List.of(), RamState.STABLE, SAMPLED_AT);
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(16 * GB, 8 * GB, SAMPLED_AT);

        MemoryEvent event = MemoryEvent.from(analyzed, memory);

        assertNull(event.topConsumer());
        assertTrue(event.findTopConsumer().isEmpty());
    }

    @Test
    void constructor_rejectsInvalidValues() {
        assertThrows(NullPointerException.class,
                () -> new MemoryEvent(null, RamState.STABLE, 16 * GB, 8 * GB, 8 * GB, null));
        assertThrows(NullPointerException.class,
                () -> new MemoryEvent(SAMPLED_AT, null, 16 * GB, 8 * GB, 8 * GB, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryEvent(SAMPLED_AT, RamState.STABLE, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MemoryEvent(SAMPLED_AT, RamState.STABLE, 16 * GB, -1, 8 * GB, null));
    }
}
