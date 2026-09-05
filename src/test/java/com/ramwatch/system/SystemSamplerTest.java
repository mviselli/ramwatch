package com.ramwatch.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SystemSamplerTest {

    @Test
    void displayName_keepsAnInformativeName() {
        assertEquals("Brave Browser Helper (Renderer)",
                SystemSampler.displayName("Brave Browser Helper (Renderer)", "/Applications/Brave.app/x --type=renderer"));
    }

    @Test
    void displayName_fallsBackToTheCommand_whenTheNameIsJustAVersion() {
        assertEquals("claude", SystemSampler.displayName("2.1.261", "claude --resume"));
        assertEquals("node", SystemSampler.displayName("18.20.4", "/usr/local/bin/node server.js"));
    }

    @Test
    void displayName_keepsTheOriginal_whenTheCommandIsUnhelpful() {
        assertEquals("2.1.261", SystemSampler.displayName("2.1.261", null));
        assertEquals("2.1.261", SystemSampler.displayName("2.1.261", "   "));
        assertEquals("2.1.261",
                SystemSampler.displayName("2.1.261", "/Users/me/.local/share/claude/versions/2.1.261 --resume"));
    }

    @Test
    void displayName_handlesAMissingName() {
        assertEquals("claude", SystemSampler.displayName("", "claude --resume"));
        assertEquals("unknown", SystemSampler.displayName(null, null));
    }

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
