package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TrackingExecutor} (Listing 7.21).
 *
 * Verifies that:
 *   - Tasks running at shutdownNow time are recorded as cancelled-in-progress.
 *   - Tasks that finished normally before shutdown are NOT recorded.
 *   - Calling getCancelledTasks before termination throws.
 */
class TrackingExecutorTest {

    @Test
    void getCancelledTasks_beforeTermination_throws() {
        ExecutorService backing = Executors.newSingleThreadExecutor();
        TrackingExecutor exec = new TrackingExecutor(backing);
        try {
            assertThrows(IllegalStateException.class, exec::getCancelledTasks);
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void runningTasksAtShutdown_areRecordedAsCancelled() throws InterruptedException {
        ExecutorService backing = Executors.newFixedThreadPool(2);
        TrackingExecutor exec = new TrackingExecutor(backing);

        CountDownLatch started = new CountDownLatch(2);
        AtomicInteger interruptedSeen = new AtomicInteger();
        // Use DISTINCT Runnable instances — the tracker dedups by identity.
        for (int i = 0; i < 2; i++) {
            exec.execute(new Runnable() {
                @Override public void run() {
                    started.countDown();
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        interruptedSeen.incrementAndGet();
                        Thread.currentThread().interrupt();   // preserve flag for tracker
                    }
                }
            });
        }
        assertTrue(started.await(2, TimeUnit.SECONDS));

        exec.shutdownNow();
        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(2, interruptedSeen.get());
        List<Runnable> cancelled = exec.getCancelledTasks();
        assertEquals(2, cancelled.size(),
                "both running tasks should be tracked as cancelled-in-progress");
    }

    @Test
    void normallyCompletedTasks_areNotRecordedAsCancelled() throws InterruptedException {
        ExecutorService backing = Executors.newSingleThreadExecutor();
        TrackingExecutor exec = new TrackingExecutor(backing);

        AtomicInteger ran = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            exec.execute(ran::incrementAndGet);
        }
        exec.shutdown();
        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));

        assertEquals(5, ran.get());
        assertTrue(exec.getCancelledTasks().isEmpty(),
                "tasks that finished before shutdown should not be tracked as cancelled");
    }
}
