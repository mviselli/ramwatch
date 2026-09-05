package com.ramwatch.storage;

import com.ramwatch.analysis.AnalyzedSnapshot;
import com.ramwatch.analysis.MemoryAnalyzer;
import com.ramwatch.analysis.StateTracker;
import com.ramwatch.system.MemorySnapshot;
import com.ramwatch.system.ProcessSnapshot;
import com.ramwatch.system.SystemSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The analyzer → tracker → logger pipeline that {@code DashboardController} runs on
 * every polling cycle: an event reaches the log only when a threshold is crossed.
 */
class ThresholdLoggingIntegrationTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final long TOTAL = 16 * GB;

    @TempDir
    Path tmp;

    private final MemoryAnalyzer analyzer = new MemoryAnalyzer(20.0, 10.0, 0, 10);
    private final StateTracker tracker = new StateTracker();

    /** One polling cycle with the given amount of free RAM. */
    private void poll(EventLogger logger, double freeGb) {
        MemorySnapshot memory = MemorySnapshot.fromTotalAndAvailable(
                TOTAL, (long) (freeGb * GB), Instant.now());
        SystemSnapshot snapshot = SystemSnapshot.fromMemoryAndProcesses(
                memory, List.of(new ProcessSnapshot(1234, "Chrome", 4 * GB)));

        AnalyzedSnapshot analyzed = analyzer.analyze(snapshot);
        if (tracker.accept(analyzed.state())) {
            logger.log(MemoryEvent.from(analyzed, snapshot.memory()));
        }
    }

    @Test
    void logsOncePerThresholdCrossing_notOncePerPoll() throws IOException {
        Path file = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(file, true);

        poll(logger, 8.0);   // stable, nothing to report
        poll(logger, 8.0);
        poll(logger, 2.0);   // 12.5% free → warning
        poll(logger, 2.1);   // still warning
        poll(logger, 1.0);   // 6.25% free → critical
        poll(logger, 0.9);   // still critical
        poll(logger, 8.0);   // recovered

        List<String> lines = Files.readAllLines(file);
        assertEquals(3, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("WARNING"), lines.get(0));
        assertTrue(lines.get(1).contains("CRITICAL"), lines.get(1));
        assertTrue(lines.get(2).contains("STABLE"), lines.get(2));
        assertTrue(lines.get(1).contains("top=Chrome pid=1234"), lines.get(1));
    }

    @Test
    void writesNothing_whileLoggingIsDisabled() {
        Path file = tmp.resolve("events.log");
        EventLogger logger = new EventLogger(file, false);

        poll(logger, 8.0);
        poll(logger, 0.5);

        assertFalse(Files.exists(file));
    }
}
