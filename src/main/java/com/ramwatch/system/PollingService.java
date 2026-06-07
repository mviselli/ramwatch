package com.ramwatch.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PollingService {

    public static final int MIN_INTERVAL_SECONDS = 1;

    private final SystemSampler sampler;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> task;

    public PollingService(SystemSampler sampler) {
        this.sampler = sampler;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ramwatch-poller");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(int intervalSeconds, Consumer<SystemSnapshot> listener) {
        int interval = Math.max(intervalSeconds, MIN_INTERVAL_SECONDS);
        task = executor.scheduleAtFixedRate(() -> {
            try {
                listener.accept(sampler.sample());
            } catch (Exception ignored) {}
        }, 0, interval, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
        executor.shutdown();
    }
}
