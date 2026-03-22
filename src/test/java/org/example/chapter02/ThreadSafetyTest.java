package org.example.chapter02;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the thread safety concepts from Chapter 2:
 *
 * - Stateless objects are always thread-safe
 * - Unsafe counting produces lost updates (race condition)
 * - AtomicLong-based counting is correct under contention
 * - Lazy initialization race condition produces multiple instances
 * - Reentrant locking allows synchronized super calls
 * - CachedFactorizer balances safety and concurrency
 */
class ThreadSafetyTest {

    private static final int THREADS = 10;
    private static final int OPS_PER_THREAD = 1_000;
    private static final int TOTAL = THREADS * OPS_PER_THREAD;

    // ---------------------------------------------------------------
    // Listing 2.1 — Stateless objects are always thread-safe
    // ---------------------------------------------------------------

    @Test
    void statelessFactorizer_isThreadSafe() throws InterruptedException {
        StatelessFactorizer factorizer = new StatelessFactorizer();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        Set<Boolean> allCorrect = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < OPS_PER_THREAD; j++) {
                    BigInteger num = BigInteger.valueOf(28); // 2 * 2 * 7
                    List<BigInteger> factors = factorizer.factor(num);
                    boolean correct = factors.equals(List.of(
                            BigInteger.TWO, BigInteger.TWO, BigInteger.valueOf(7)));
                    allCorrect.add(correct);
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // Every single result should be correct — no thread interference
        assertTrue(allCorrect.contains(true), "Should have correct results");
        assertFalse(allCorrect.contains(false), "Stateless factorizer should never produce wrong results");
    }

    // ---------------------------------------------------------------
    // Listing 2.2 — Unsafe counting loses updates
    // ---------------------------------------------------------------

    @Test
    void unsafeCountingFactorizer_losesUpdatesUnderContention() throws InterruptedException {
        UnsafeCountingFactorizer counter = new UnsafeCountingFactorizer();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < OPS_PER_THREAD; j++) {
                    counter.service();
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // With a race condition, count should be less than TOTAL due to lost updates.
        // This is probabilistic — with 10 threads × 1000 ops it almost always shows loss.
        System.out.println("UnsafeCountingFactorizer: " + counter.getCount() + " out of " + TOTAL);
        assertTrue(counter.getCount() < TOTAL,
                "Expected lost updates from UnsafeCountingFactorizer due to race condition. " +
                "This can occasionally pass — rerun the test.");
    }

    // ---------------------------------------------------------------
    // Listing 2.4 — AtomicLong counting is correct
    // ---------------------------------------------------------------

    @Test
    void countingFactorizer_neverLosesUpdates() throws InterruptedException {
        CountingFactorizer counter = new CountingFactorizer();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < OPS_PER_THREAD; j++) {
                    counter.service();
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // AtomicLong guarantees no lost updates
        System.out.println("CountingFactorizer: " + counter.getCount() + " out of " + TOTAL);
        assertEquals(TOTAL, counter.getCount(),
                "AtomicLong should produce exactly " + TOTAL + " — no lost updates");
    }

    // ---------------------------------------------------------------
    // Listing 2.3 — Lazy initialization race creates multiple instances
    // ---------------------------------------------------------------

    @Test
    void lazyInitRace_canCreateMultipleInstances() throws InterruptedException {
        LazyInitRace lazyInit = new LazyInitRace();
        Set<Integer> identityHashCodes = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // Each thread grabs the instance and records its identity
                LazyInitRace.ExpensiveObject obj = lazyInit.getInstance();
                identityHashCodes.add(System.identityHashCode(obj));
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // If the race fires, multiple distinct objects are created.
        // This is probabilistic — we just report the observation.
        System.out.println("LazyInitRace: " + identityHashCodes.size() + " distinct instance(s) observed");
        if (identityHashCodes.size() > 1) {
            System.out.println("  → Race condition detected: multiple instances created!");
        }
    }

    // ---------------------------------------------------------------
    // Listing 2.7 — Reentrant locking does not deadlock
    // ---------------------------------------------------------------

    @Test
    void loggingWidget_doesNotDeadlock() {
        LoggingWidget widget = new LoggingWidget();

        // If intrinsic locks were not reentrant, this would deadlock.
        // The subclass synchronized doSomething() calls super.doSomething()
        // which also requires the lock on `this`.
        assertDoesNotThrow(widget::doSomething,
                "Reentrant locks allow synchronized super calls without deadlock");
    }

    // ---------------------------------------------------------------
    // Listing 2.8 — CachedFactorizer: correct and concurrent
    // ---------------------------------------------------------------

    @Test
    void cachedFactorizer_returnsCorrectResultsUnderContention() throws InterruptedException {
        CachedFactorizer cached = new CachedFactorizer();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        AtomicReference<AssertionError> failure = new AtomicReference<>();

        BigInteger[] testNumbers = {
                BigInteger.valueOf(12),   // 2, 2, 3
                BigInteger.valueOf(17),   // 17 (prime)
                BigInteger.valueOf(100),  // 2, 2, 5, 5
        };

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        BigInteger num = testNumbers[j % testNumbers.length];
                        List<BigInteger> factors = cached.service(num);

                        // Verify the factors multiply back to the original number
                        BigInteger product = factors.stream()
                                .reduce(BigInteger.ONE, BigInteger::multiply);
                        assertEquals(num, product,
                                "Factors of " + num + " should multiply back: " + factors);
                    }
                } catch (AssertionError e) {
                    failure.compareAndSet(null, e);
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        if (failure.get() != null) {
            throw failure.get();
        }

        System.out.println("CachedFactorizer: " + cached.getHits() + " hits, "
                + String.format("%.1f%%", cached.getCacheHitRatio() * 100) + " cache hit ratio");
    }

    // ---------------------------------------------------------------
    // Listing 2.6 — SynchronizedFactorizer: correct but slow
    // ---------------------------------------------------------------

    @Test
    void synchronizedFactorizer_isCorrectButSlow() throws InterruptedException {
        SynchronizedFactorizer sync = new SynchronizedFactorizer();
        CachedFactorizer cached = new CachedFactorizer();
        BigInteger number = BigInteger.valueOf(1_000_003); // prime — forces full computation

        int iterations = 500;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGateSynced = new CountDownLatch(THREADS);
        CountDownLatch endGateCached = new CountDownLatch(THREADS);

        // Time the fully-synchronized version
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < iterations; j++) {
                    sync.service(number);
                }
                endGateSynced.countDown();
            });
            t.start();
        }

        long syncStart = System.nanoTime();
        startGate.countDown();
        endGateSynced.await();
        long syncTime = System.nanoTime() - syncStart;

        // Time the narrowly-synchronized version
        CountDownLatch startGate2 = new CountDownLatch(1);
        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try { startGate2.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < iterations; j++) {
                    cached.service(number);
                }
                endGateCached.countDown();
            });
            t.start();
        }

        long cachedStart = System.nanoTime();
        startGate2.countDown();
        endGateCached.await();
        long cachedTime = System.nanoTime() - cachedStart;

        System.out.println("SynchronizedFactorizer: " + (syncTime / 1_000_000) + " ms");
        System.out.println("CachedFactorizer:       " + (cachedTime / 1_000_000) + " ms");
        System.out.println("  → CachedFactorizer benefits from narrower synchronization and caching");

        // Both should produce correct results — we just observe the performance difference
        List<BigInteger> result = sync.service(number);
        BigInteger product = result.stream().reduce(BigInteger.ONE, BigInteger::multiply);
        assertEquals(number, product, "SynchronizedFactorizer should return correct factors");
    }
}
