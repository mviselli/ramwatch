package com.ramwatch.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs the sampling cycle periodically on a background thread.
 *
 * <p>Sampling never happens on the JavaFX application thread: a single daemon thread named
 * {@code ramwatch-poller} drives it, and the listener is invoked on that same thread, so it
 * is up to the UI to hop back to the JavaFX thread. The thread being a daemon means it never
 * keeps the JVM alive after the window closes, and {@link #stop()} shuts it down cleanly.
 *
 * <p>The interval is clamped to {@link #MIN_INTERVAL_SECONDS}, so no configuration can make
 * the app poll more aggressively than once per second.
 *
 * @author Michele Viselli
 * @since 1.0
 */
public class PollingService {

    /** Shortest interval allowed between two samples, in seconds. */
    public static final int MIN_INTERVAL_SECONDS = 1;

    /** Source of every reading. */
    private final SystemSampler sampler;

    /** Single daemon thread running the cycles, off the JavaFX application thread. */
    private final ScheduledExecutorService executor;

    /** Handle on the scheduled cycle, cancelled on restart and on stop; {@code null} before the first start. */
    private ScheduledFuture<?> task;

    /** Listener given to {@link #start}, kept across restarts; invoked on the polling thread. */
    private Consumer<SystemSnapshot> currentListener;

    /**
     * Creates a service that will sample through the given sampler.
     *
     * <p>The scheduler thread is created here but stays idle until {@link #start} is called.
     *
     * @param sampler the sampler to invoke on every cycle; must not be {@code null}
     */
    public PollingService(SystemSampler sampler) {
        this.sampler = sampler;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ramwatch-poller");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts polling, delivering every snapshot to the given listener.
     *
     * <p>The first sample is taken immediately, then one every interval. The listener is
     * called on the polling thread, never on the UI thread.
     *
     * @param intervalSeconds delay between two samples, in seconds; values below
     *                        {@link #MIN_INTERVAL_SECONDS} are raised to it
     * @param listener        receives every snapshot; must not be {@code null}
     */
    public void start(int intervalSeconds, Consumer<SystemSnapshot> listener) {
        this.currentListener = listener;
        schedule(intervalSeconds);
    }

    /**
     * Applies a new interval without restarting the application.
     *
     * <p>The pending cycle is cancelled and a new schedule takes over, keeping the listener
     * given to {@link #start}.
     *
     * @param intervalSeconds new delay between two samples, in seconds; values below
     *                        {@link #MIN_INTERVAL_SECONDS} are raised to it
     */
    public void restart(int intervalSeconds) {
        if (task != null) task.cancel(false);
        schedule(intervalSeconds);
    }

    /**
     * Stops polling and shuts the background thread down.
     *
     * <p>Call this when the application closes. A cycle already running is allowed to finish;
     * no new one is scheduled. The service cannot be started again after this.
     */
    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
        executor.shutdown();
    }

    /**
     * Schedules the sampling task at a fixed rate, clamping the interval to the minimum.
     *
     * <p>A failing cycle is swallowed on purpose: a transient read error must not kill the
     * scheduled task and leave the app frozen on stale data.
     *
     * @param intervalSeconds requested delay between two samples, in seconds
     */
    private void schedule(int intervalSeconds) {
        int interval = Math.max(intervalSeconds, MIN_INTERVAL_SECONDS);
        task = executor.scheduleAtFixedRate(() -> {
            try {
                currentListener.accept(sampler.sample());
            } catch (Exception ignored) {}
        }, 0, interval, TimeUnit.SECONDS);
    }
}
