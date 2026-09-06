package com.ramwatch.ui;

import com.ramwatch.system.ProcessSnapshot;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRowDiffTest {

    private static final long MB = 1_048_576L;

    private static ProcessSnapshot proc(int pid, String name, long megabytes) {
        return new ProcessSnapshot(pid, name, megabytes * MB);
    }

    // ── isVisiblyDifferent ───────────────────────────────────

    @Test
    void isVisiblyDifferent_saysNo_whenTheSameReadingRepeats() {
        ProcessSnapshot shown = proc(101, "java", 400);

        assertFalse(ProcessRowDiff.isVisiblyDifferent(shown, shown));
    }

    @Test
    void isVisiblyDifferent_saysNo_whenMemoryDriftsBelowOneMegabyte() {
        ProcessSnapshot shown = proc(101, "java", 400);
        ProcessSnapshot fresh = new ProcessSnapshot(101, "java", 400 * MB + (MB - 1));

        assertFalse(ProcessRowDiff.isVisiblyDifferent(shown, fresh));
    }

    @Test
    void isVisiblyDifferent_saysYes_whenMemoryMovesByOneMegabyte() {
        assertTrue(ProcessRowDiff.isVisiblyDifferent(proc(101, "java", 400), proc(101, "java", 401)));
    }

    @Test
    void isVisiblyDifferent_saysYes_whenMemoryDropsByOneMegabyte() {
        assertTrue(ProcessRowDiff.isVisiblyDifferent(proc(101, "java", 401), proc(101, "java", 400)));
    }

    @Test
    void isVisiblyDifferent_saysYes_whenAnotherProcessTakesTheRank() {
        assertTrue(ProcessRowDiff.isVisiblyDifferent(proc(101, "java", 400), proc(202, "chrome", 400)));
    }

    @Test
    void isVisiblyDifferent_saysYes_whenOnlyTheNameChanges() {
        assertTrue(ProcessRowDiff.isVisiblyDifferent(proc(101, "java", 400), proc(101, "RamWatch", 400)));
    }

    // ── apply ────────────────────────────────────────────────

    @Test
    void apply_touchesNothing_whenTheRankingIsUnchanged() {
        ObservableList<ProcessSnapshot> rows =
                FXCollections.observableArrayList(proc(1, "java", 400), proc(2, "chrome", 300));
        List<ProcessSnapshot> latest = List.of(proc(1, "java", 400), proc(2, "chrome", 300));

        assertEquals(0, ProcessRowDiff.apply(rows, latest));
        assertEquals(latest, rows);
    }

    @Test
    void apply_touchesNothing_whenEveryRowDriftsBelowThreshold() {
        ObservableList<ProcessSnapshot> rows =
                FXCollections.observableArrayList(proc(1, "java", 400), proc(2, "chrome", 300));
        List<ProcessSnapshot> latest = List.of(
                new ProcessSnapshot(1, "java", 400 * MB + 700_000),
                new ProcessSnapshot(2, "chrome", 300 * MB - 700_000));

        assertEquals(0, ProcessRowDiff.apply(rows, latest));
    }

    @Test
    void apply_leavesTheStaleReadingOnScreen_whenItIsNotWorthRedrawing() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(proc(1, "java", 400));

        ProcessRowDiff.apply(rows, List.of(new ProcessSnapshot(1, "java", 400 * MB + 500)));

        assertEquals(400 * MB, rows.get(0).usedMemoryBytes());
    }

    @Test
    void apply_redrawsADriftingRow_onceItAccumulatesToOneMegabyte() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(proc(1, "java", 400));
        long drift = 400 * MB;

        // Each step alone stays under the threshold; the comparison is against what is shown,
        // so the row must still refresh once the accumulated drift crosses a megabyte.
        for (int i = 0; i < 3; i++) {
            drift += 400_000;
            ProcessRowDiff.apply(rows, List.of(new ProcessSnapshot(1, "java", drift)));
        }

        assertEquals(drift, rows.get(0).usedMemoryBytes());
    }

    @Test
    void apply_replacesOnlyTheRowsThatMoved() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(
                proc(1, "java", 400), proc(2, "chrome", 300), proc(3, "idea", 200));
        List<ProcessSnapshot> latest = List.of(
                proc(1, "java", 400), proc(2, "chrome", 350), proc(3, "idea", 200));

        assertEquals(1, ProcessRowDiff.apply(rows, latest));
        assertEquals(latest, rows);
    }

    @Test
    void apply_addsTheRowsAppearingAtTheTail() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(proc(1, "java", 400));
        List<ProcessSnapshot> latest = List.of(proc(1, "java", 400), proc(2, "chrome", 300));

        assertEquals(1, ProcessRowDiff.apply(rows, latest));
        assertEquals(latest, rows);
    }

    @Test
    void apply_dropsTheRowsOfProcessesThatAreGone() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(
                proc(1, "java", 400), proc(2, "chrome", 300), proc(3, "idea", 200));
        List<ProcessSnapshot> latest = List.of(proc(1, "java", 400));

        assertEquals(2, ProcessRowDiff.apply(rows, latest));
        assertEquals(latest, rows);
    }

    @Test
    void apply_emptiesTheTable_whenNoProcessPassesTheFilter() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(proc(1, "java", 400));

        assertEquals(1, ProcessRowDiff.apply(rows, List.of()));
        assertTrue(rows.isEmpty());
    }

    @Test
    void apply_movesARowUp_whenAProcessOvertakesAnother() {
        ObservableList<ProcessSnapshot> rows = FXCollections.observableArrayList(
                proc(1, "java", 400), proc(2, "chrome", 300));
        List<ProcessSnapshot> latest = List.of(proc(2, "chrome", 500), proc(1, "java", 400));

        assertEquals(2, ProcessRowDiff.apply(rows, latest));
        assertEquals(latest, rows);
    }
}
