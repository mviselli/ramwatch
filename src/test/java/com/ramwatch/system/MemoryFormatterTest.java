package com.ramwatch.system;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MemoryFormatterTest {

    @Test
    void formatsBelowGbAsMb() {
        assertEquals("512 MB", MemoryFormatter.formatBytes(512 * 1_048_576L));
    }

    @Test
    void formatsAboveGbWithTwoDecimals() {
        assertEquals("1.50 GB", MemoryFormatter.formatBytes((long) (1.5 * 1_073_741_824L)));
    }

    @Test
    void formatsPercent() {
        assertEquals("75.0%", MemoryFormatter.formatPercent(0.75));
    }

    @Test
    void usageRatioIsCorrect() {
        long total = 8L * 1_073_741_824L;
        long available = 2L * 1_073_741_824L;
        MemorySnapshot snapshot = MemorySnapshot.fromTotalAndAvailable(total, available, Instant.now());
        assertEquals(0.75, MemoryFormatter.usageRatio(snapshot), 1e-9);
    }
}
