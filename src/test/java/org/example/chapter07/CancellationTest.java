package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the task-cancellation listings in section 7.1.
 */
class CancellationTest {

    // -----------------------------------------------------------------
    // Listing 7.1 — volatile-flag cancellation
    // -----------------------------------------------------------------

    @Test
    void primeGenerator_stopsPromptlyAfterCancel() throws InterruptedException {
        PrimeGenerator gen = new PrimeGenerator();
        Thread t = new Thread(gen);
        t.start();
        try {
            MILLISECONDS.sleep(50);
        } finally {
            gen.cancel();
        }
        t.join(2_000);
        assertFalse(t.isAlive(), "PrimeGenerator should have exited after cancel()");
        List<BigInteger> primes = gen.get();
        assertFalse(primes.isEmpty(), "should have produced at least one prime");
    }

    // -----------------------------------------------------------------
    // Listing 7.3 — broken cancellation (demonstration)
    // -----------------------------------------------------------------

    @Test
    void brokenPrimeProducer_wedgesWhenQueueFillsUp() throws InterruptedException {
        BlockingQueue<BigInteger> queue = new ArrayBlockingQueue<>(4);
        BrokenPrimeProducer producer = new BrokenPrimeProducer(queue);
        producer.start();

        // Let the queue fill.
        MILLISECONDS.sleep(100);
        assertEquals(4, queue.size(), "queue should be full while producer is wedged in put");

        // Setting the cancelled flag does NOT unblock the producer.
        producer.cancel();
        MILLISECONDS.sleep(100);
        assertTrue(producer.isAlive(),
                "BrokenPrimeProducer should still be wedged on put() — the bug we're demonstrating");

        // Force-stop: only interrupt actually unblocks it (which is exactly the fix).
        producer.interrupt();
        producer.join(1_000);
        assertFalse(producer.isAlive(), "interrupt should have freed the producer");
    }

    // -----------------------------------------------------------------
    // Listing 7.5 — interruption-based cancellation
    // -----------------------------------------------------------------

    @Test
    void primeProducer_exitsOnInterrupt() throws InterruptedException {
        BlockingQueue<BigInteger> queue = new ArrayBlockingQueue<>(4);
        PrimeProducer producer = new PrimeProducer(queue);
        producer.start();

        MILLISECONDS.sleep(100);
        assertEquals(4, queue.size(), "queue should fill before we cancel");

        producer.cancel();          // calls interrupt()
        producer.join(1_000);
        assertFalse(producer.isAlive(), "producer should exit promptly on interrupt");
    }

    // -----------------------------------------------------------------
    // Listing 7.7 — non-cancellable task that restores interruption
    // -----------------------------------------------------------------

    @Test
    void takeRetryingOnInterrupt_returnsItemAndRestoresFlag() throws InterruptedException {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>();

        Thread caller = new Thread(() -> {
            String taken = NoncancelableTask.takeRetryingOnInterrupt(queue);
            assertEquals("payload", taken);
            // The thread should still observe its own interrupted status.
            assertTrue(Thread.currentThread().isInterrupted(),
                    "interrupted status must be restored on exit");
        });
        caller.start();

        // Interrupt before producing — the helper should swallow, retry, then succeed.
        caller.interrupt();
        MILLISECONDS.sleep(50);
        queue.put("payload");

        caller.join(1_000);
        assertFalse(caller.isAlive());
    }

    // -----------------------------------------------------------------
    // Listing 7.10 — Future-based timed run
    // -----------------------------------------------------------------

    @Test
    void timedRun_returnsAfterTimeoutAndCancelsTask() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            long start = System.nanoTime();
            TimedRun.timedRun(exec, () -> {
                try { Thread.sleep(5_000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, 100, MILLISECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs < 500,
                    () -> "timedRun should return shortly after the deadline; took " + elapsedMs + "ms");
        } finally {
            exec.shutdownNow();
            assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void timedRun_returnsImmediatelyWhenTaskIsFast() throws InterruptedException {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            long start = System.nanoTime();
            TimedRun.timedRun(exec, () -> { /* do nothing */ }, 5, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs < 500,
                    () -> "fast task should not wait the full deadline; took " + elapsedMs + "ms");
        } finally {
            exec.shutdownNow();
            assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void timedRun_propagatesUncheckedExceptionFromTask() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            RuntimeException re = assertThrows(RuntimeException.class, () ->
                    TimedRun.timedRun(exec, () -> { throw new IllegalArgumentException("boom"); },
                            1, TimeUnit.SECONDS));
            assertInstanceOf(IllegalArgumentException.class, re);
            assertEquals("boom", re.getMessage());
        } finally {
            exec.shutdownNow();
        }
    }
}
