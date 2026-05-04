package org.example.chapter05;

import java.util.concurrent.CountDownLatch;

/**
 * Listing 5.11 - Using CountDownLatch for Starting and Stopping Threads
 * in Timing Tests.
 *
 * Two latches:
 *   - startGate (count = 1): every worker awaits it. The master counts
 *     it down ONCE so all workers are released simultaneously. Without
 *     this, threads created earlier get a head start, and the mix of
 *     active threads changes over time — bad for measuring concurrent
 *     behavior.
 *   - endGate (count = nThreads): each worker counts down on finish.
 *     The master awaits it, blocking until the LAST worker finishes,
 *     so the elapsed time covers the whole group.
 *
 * This pattern is the canonical way to benchmark concurrent tasks.
 */
public class TestHarness {

    public long timeTasks(int nThreads, final Runnable task) throws InterruptedException {
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(nThreads);

        for (int i = 0; i < nThreads; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    try {
                        task.run();
                    } finally {
                        endGate.countDown();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            t.start();
        }

        long start = System.nanoTime();
        startGate.countDown();   // release everyone at once
        endGate.await();         // block until the last one finishes
        long end = System.nanoTime();
        return end - start;
    }
}
