package com.ramwatch.analysis;

import java.util.Objects;

/**
 * Remembers the last observed {@link RamState} so callers can react to threshold
 * crossings only, instead of to every polling cycle.
 *
 * <p>Tracking starts from {@link RamState#STABLE}: a session that opens already in
 * warning or critical reports a transition on its first sample, while one that opens
 * in a healthy state stays quiet. Recoveries back to {@code STABLE} are transitions
 * too, so the caller can clear an alert and record the return to normal.
 *
 * <p>Not thread-safe: call it from the polling thread only.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public final class StateTracker {

    /** Last state accepted, or {@link RamState#STABLE} before the first sample and after a reset. */
    private RamState previous = RamState.STABLE;

    /** Creates a tracker that starts from {@link RamState#STABLE}. */
    public StateTracker() {}

    /**
     * Records the freshly observed state.
     *
     * @param current the state observed in this polling cycle; must not be {@code null}
     * @return {@code true} if it differs from the previous one, i.e. a threshold was crossed.
     * @throws NullPointerException if {@code current} is {@code null}
     */
    public boolean accept(RamState current) {
        Objects.requireNonNull(current, "current must not be null");
        if (current == previous) {
            return false;
        }
        previous = current;
        return true;
    }

    /**
     * The state last accepted, without changing anything.
     *
     * @return the current state, {@link RamState#STABLE} if nothing was accepted yet
     */
    public RamState currentState() {
        return previous;
    }

    /**
     * Forgets the history, e.g. after the user changed the thresholds.
     *
     * <p>Tracking restarts from {@link RamState#STABLE}, so the next sample taken in warning
     * or critical is reported as a fresh transition against the new thresholds.
     */
    public void reset() {
        previous = RamState.STABLE;
    }
}
