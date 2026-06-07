package com.ramwatch.system;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PollingServiceTest {

    @Test
    void listenerIsCalledAfterStart() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        PollingService service = new PollingService(new SystemSampler());
        service.start(1, snapshot -> latch.countDown());

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        service.stop();
    }

    @Test
    void intervalBelowMinimumIsClamped() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        PollingService service = new PollingService(new SystemSampler());
        // passing 0 should be clamped to 1 second
        service.start(0, snapshot -> count.incrementAndGet());

        Thread.sleep(2500);
        service.stop();

        // with 1s minimum and 2.5s wait we expect 2-3 calls, not 20+
        assertTrue(count.get() <= 4, "Interval was not clamped: " + count.get() + " calls");
    }

    @Test
    void stopHaltsPolling() throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        PollingService service = new PollingService(new SystemSampler());
        service.start(1, snapshot -> count.incrementAndGet());
        Thread.sleep(500);
        service.stop();
        int after = count.get();
        Thread.sleep(2000);
        assertEquals(after, count.get(), "Polling continued after stop");
    }
}
