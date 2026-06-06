package com.ramwatch.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemSamplerTest {

    @Test
    void sampleReturnsValidSnapshot() {
        SystemSampler sampler = new SystemSampler();
        SystemSnapshot snapshot = sampler.sample();

        assertNotNull(snapshot);
        assertNotNull(snapshot.memory());
        assertTrue(snapshot.memory().totalBytes() > 0);
        assertTrue(snapshot.memory().availableBytes() >= 0);
        assertNotNull(snapshot.processes());
        assertFalse(snapshot.processes().isEmpty());
    }

    @Test
    void sampleMemoryValuesAreConsistent() {
        SystemSnapshot snapshot = new SystemSampler().sample();
        MemorySnapshot mem = snapshot.memory();

        assertEquals(mem.usedBytes(), mem.totalBytes() - mem.availableBytes());
        assertTrue(mem.usedBytes() >= 0);
        assertTrue(mem.availableBytes() <= mem.totalBytes());
    }

    @Test
    void sampleProcessesHaveValidFields() {
        SystemSnapshot snapshot = new SystemSampler().sample();

        for (ProcessSnapshot p : snapshot.processes()) {
            assertTrue(p.pid() > 0, "PID must be positive");
            assertNotNull(p.name());
            assertFalse(p.name().isBlank(), "Process name must not be blank");
            assertTrue(p.usedMemoryBytes() >= 0);
        }
    }
}
