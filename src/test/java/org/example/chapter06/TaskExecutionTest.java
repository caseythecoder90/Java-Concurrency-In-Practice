package org.example.chapter06;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the chapter 6 executor utilities.
 */
class TaskExecutionTest {

    // ---------------------------------------------------------------
    // Listing 6.4 - ThreadPerTaskExecutor
    // ---------------------------------------------------------------

    @Test
    void threadPerTaskExecutor_runsEachTaskOnANewNonCallerThread() throws InterruptedException {
        ThreadPerTaskExecutor exec = new ThreadPerTaskExecutor();
        int n = 8;
        CountDownLatch done = new CountDownLatch(n);
        List<Long> threadIds = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            exec.execute(() -> {
                synchronized (threadIds) {
                    threadIds.add(Thread.currentThread().threadId());
                }
                done.countDown();
            });
        }
        assertTrue(done.await(2, TimeUnit.SECONDS));

        long callerId = Thread.currentThread().threadId();
        assertEquals(n, threadIds.size());
        assertFalse(threadIds.contains(callerId), "tasks should not run on caller thread");
        // Each task should have a distinct thread id (new thread per task).
        long distinct = threadIds.stream().distinct().count();
        assertEquals(n, distinct, "expected one thread per task");
    }

    // ---------------------------------------------------------------
    // Listing 6.5 - WithinThreadExecutor
    // ---------------------------------------------------------------

    @Test
    void withinThreadExecutor_runsInTheCallersThread() {
        Executor exec = new WithinThreadExecutor();
        long callerId = Thread.currentThread().threadId();
        AtomicInteger taskThreadId = new AtomicInteger();

        exec.execute(() -> taskThreadId.set((int) Thread.currentThread().threadId()));

        assertEquals(callerId, taskThreadId.get(),
                "WithinThreadExecutor must run inline on the caller's thread");
    }

    @Test
    void withinThreadExecutor_runsTasksInSubmissionOrder() {
        Executor exec = new WithinThreadExecutor();
        List<Integer> seen = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            exec.execute(() -> seen.add(idx));
        }
        assertEquals(List.of(0, 1, 2, 3, 4), seen);
    }

    // ---------------------------------------------------------------
    // ExecutorService lifecycle (Listing 6.6 / 6.7)
    // ---------------------------------------------------------------

    @Test
    void shutdown_completesAlreadyQueuedWorkButRejectsNewSubmissions() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        AtomicInteger ran = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            exec.execute(() -> {
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ran.incrementAndGet();
            });
        }
        exec.shutdown();

        assertThrows(RejectedExecutionException.class,
                () -> exec.execute(() -> {}),
                "submission after shutdown must be rejected");

        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(3, ran.get(),
                "shutdown drains already-queued tasks rather than cancelling them");
        assertTrue(exec.isShutdown());
        assertTrue(exec.isTerminated());
    }

    @Test
    void shutdownNow_returnsUnstartedTasksAndInterruptsRunningOnes() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger interruptedCount = new AtomicInteger();

        exec.execute(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interruptedCount.incrementAndGet();
                Thread.currentThread().interrupt();
            }
        });
        // Two tasks queued behind the running one — they never start.
        Runnable queued1 = () -> {};
        Runnable queued2 = () -> {};
        exec.execute(queued1);
        exec.execute(queued2);

        assertTrue(started.await(1, TimeUnit.SECONDS));

        List<Runnable> drained = exec.shutdownNow();
        assertEquals(2, drained.size(), "should have drained the unstarted tasks");
        assertTrue(exec.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals(1, interruptedCount.get(),
                "the running task should have observed interruption");
    }

    // ---------------------------------------------------------------
    // Listing 6.16 - Per-task timeout with cancellation
    // ---------------------------------------------------------------

    @Test
    void timedFuture_returnsDefaultAndCancelsOnTimeout() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            String result = TimedFutureExample.getOrDefault(
                    exec,
                    () -> { Thread.sleep(5_000); return "should-never-return"; },
                    100,
                    "default");
            assertEquals("default", result);
        } finally {
            exec.shutdownNow();
            assertTrue(exec.awaitTermination(1, TimeUnit.SECONDS),
                    "timed-out task must respond to interrupt promptly");
        }
    }
}
