package com.ramwatch.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StateTrackerTest {

    @Test
    void accept_reportsNoTransition_whenStateRepeats() {
        StateTracker tracker = new StateTracker();

        assertTrue(tracker.accept(RamState.WARNING));
        assertFalse(tracker.accept(RamState.WARNING));
        assertFalse(tracker.accept(RamState.WARNING));
    }

    @Test
    void accept_staysQuiet_whenSessionStartsStable() {
        StateTracker tracker = new StateTracker();

        assertFalse(tracker.accept(RamState.STABLE));
        assertEquals(RamState.STABLE, tracker.currentState());
    }

    @Test
    void accept_reportsTransition_whenSessionStartsAlreadyCritical() {
        assertTrue(new StateTracker().accept(RamState.CRITICAL));
    }

    @Test
    void accept_reportsEachThresholdCrossing() {
        StateTracker tracker = new StateTracker();

        assertTrue(tracker.accept(RamState.WARNING));
        assertTrue(tracker.accept(RamState.CRITICAL));
        assertFalse(tracker.accept(RamState.CRITICAL));
        assertTrue(tracker.accept(RamState.WARNING));
        assertEquals(RamState.WARNING, tracker.currentState());
    }

    @Test
    void accept_reportsRecoveryToStable() {
        StateTracker tracker = new StateTracker();
        tracker.accept(RamState.CRITICAL);

        assertTrue(tracker.accept(RamState.STABLE));
        assertFalse(tracker.accept(RamState.STABLE));
    }

    @Test
    void reset_makesNextCriticalSampleATransitionAgain() {
        StateTracker tracker = new StateTracker();
        tracker.accept(RamState.CRITICAL);
        assertFalse(tracker.accept(RamState.CRITICAL));

        tracker.reset();

        assertEquals(RamState.STABLE, tracker.currentState());
        assertTrue(tracker.accept(RamState.CRITICAL));
    }

    @Test
    void accept_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new StateTracker().accept(null));
    }
}
