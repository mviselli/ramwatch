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
 */
public final class StateTracker {

    private RamState previous = RamState.STABLE;

    /**
     * Records the freshly observed state.
     *
     * @return {@code true} if it differs from the previous one, i.e. a threshold was crossed.
     */
    public boolean accept(RamState current) {
        Objects.requireNonNull(current, "current must not be null");
        if (current == previous) {
            return false;
        }
        previous = current;
        return true;
    }

    public RamState currentState() {
        return previous;
    }

    /** Forgets the history, e.g. after the user changed the thresholds. */
    public void reset() {
        previous = RamState.STABLE;
    }
}
