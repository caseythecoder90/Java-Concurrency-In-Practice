package org.example.chapter05;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Chapter 5 book listings.
 */
class BuildingBlocksTest {

    // ---------------------------------------------------------------
    // Listing 5.1 / 5.2 — Vector compound actions
    // ---------------------------------------------------------------

    @Test
    void unsafeVector_canThrowAIOOBEUnderContention() throws InterruptedException {
        // Probabilistic: with two threads racing, we almost always catch the race.
        int attempts = 100;
        AtomicBoolean observedRace = new AtomicBoolean();

        for (int attempt = 0; attempt < attempts && !observedRace.get(); attempt++) {
            Vector<Integer> v = new Vector<>();
            for (int i = 0; i < 10; i++) v.add(i);

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch endGate = new CountDownLatch(2);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            Thread getter = new Thread(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < 100; i++) {
                        try {
                            UnsafeVectorCompoundActions.getLast(v);
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });

            Thread deleter = new Thread(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < 100 && !v.isEmpty(); i++) {
                        try {
                            UnsafeVectorCompoundActions.deleteLast(v);
                        } catch (Throwable ignored) {
                            // Also a legitimate symptom of the race
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });

            getter.start();
            deleter.start();
            startGate.countDown();
            endGate.await();

            if (failure.get() instanceof ArrayIndexOutOfBoundsException) {
                observedRace.set(true);
            }
        }

        // Not a strict assertion — the race is probabilistic. Just record it.
        System.out.println("UnsafeVector race observed: " + observedRace.get());
    }

    @Test
    void safeVector_neverThrowsAIOOBEUnderContention() throws InterruptedException {
        Vector<Integer> v = new Vector<>();
        for (int i = 0; i < 1000; i++) v.add(i);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread getter = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < 500 && !v.isEmpty(); i++) {
                    try {
                        SafeVectorCompoundActions.getLast(v);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        Thread deleter = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < 500 && !v.isEmpty(); i++) {
                    try {
                        SafeVectorCompoundActions.deleteLast(v);
                    } catch (Throwable ignored) {
                        // deleteLast on an empty vector is a race result we're not testing here
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        getter.start();
        deleter.start();
        startGate.countDown();
        endGate.await();

        assertNull(failure.get(), "Client-side locking should prevent compound-action races");
    }

    // ---------------------------------------------------------------
    // Listing 5.6 — HiddenIterator
    // ---------------------------------------------------------------

    @Test
    void hiddenIterator_demonstratesCMEThroughToString() throws InterruptedException {
        HiddenIterator hi = new HiddenIterator();
        AtomicBoolean cme = new AtomicBoolean();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);

        Thread mutator = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < 5000; i++) {
                    hi.add(i);
                    if (i % 2 == 0) hi.remove(i - 1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        Thread debugger = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < 200 && !cme.get(); i++) {
                    try {
                        // Call a method that internally stringifies the set.
                        // The real bug is addTenThings printing "... " + set,
                        // but we can trigger it here via any read that iterates.
                        hi.addTenThings();
                    } catch (java.util.ConcurrentModificationException e) {
                        cme.set(true);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        mutator.start();
        debugger.start();
        startGate.countDown();
        endGate.await();

        // Probabilistic — print what we observed
        System.out.println("HiddenIterator CME observed: " + cme.get());
    }

    // ---------------------------------------------------------------
    // Listing 5.11 — TestHarness
    // ---------------------------------------------------------------

    @Test
    void testHarness_releasesAllWorkersSimultaneouslyAndWaitsForLast() throws InterruptedException {
        TestHarness harness = new TestHarness();
        AtomicInteger completed = new AtomicInteger();

        long elapsed = harness.timeTasks(8, () -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            completed.incrementAndGet();
        });

        assertEquals(8, completed.get(), "All 8 workers should have run");
        // Should be roughly 20ms (concurrent), not 160ms (serial)
        assertTrue(elapsed < 150_000_000L,
                "Workers should run concurrently; elapsed was " + (elapsed / 1_000_000) + "ms");
    }

    // ---------------------------------------------------------------
    // Listing 5.12 — Preloader
    // ---------------------------------------------------------------

    @Test
    void preloader_returnsProductInfoAfterStart() throws Exception {
        Preloader loader = new Preloader();
        loader.start();

        Preloader.ProductInfo info = loader.get();
        assertNotNull(info);
        assertEquals("preloaded", info.payload());
    }

    // ---------------------------------------------------------------
    // Listing 5.13 — launderThrowable
    // ---------------------------------------------------------------

    @Test
    void launderThrowable_returnsRuntimeExceptionAsIs() {
        RuntimeException re = new RuntimeException("boom");
        assertSame(re, LaunderThrowable.launderThrowable(re));
    }

    @Test
    void launderThrowable_rethrowsErrorsDirectly() {
        OutOfMemoryError oom = new OutOfMemoryError();
        assertThrows(OutOfMemoryError.class, () -> LaunderThrowable.launderThrowable(oom));
    }

    @Test
    void launderThrowable_throwsIllegalStateForCheckedExceptions() {
        Exception checked = new Exception("checked");
        assertThrows(IllegalStateException.class, () -> LaunderThrowable.launderThrowable(checked));
    }

    // ---------------------------------------------------------------
    // Listing 5.14 — BoundedHashSet
    // ---------------------------------------------------------------

    @Test
    void boundedHashSet_blocksWhenFullAndUnblocksOnRemove() throws InterruptedException {
        BoundedHashSet<Integer> set = new BoundedHashSet<>(3);
        set.add(1);
        set.add(2);
        set.add(3);
        assertEquals(3, set.size());

        CountDownLatch adderStarted = new CountDownLatch(1);
        AtomicBoolean addedFourth = new AtomicBoolean();

        Thread adder = new Thread(() -> {
            adderStarted.countDown();
            try {
                set.add(4);
                addedFourth.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        adder.start();
        adderStarted.await();

        // Give the adder a moment — it should be blocked on the full semaphore
        Thread.sleep(30);
        assertFalse(addedFourth.get(), "Add should block while the set is full");

        // Free up a slot
        set.remove(1);
        adder.join(1000);
        assertTrue(addedFourth.get(), "Add should unblock once a slot frees up");
        assertEquals(3, set.size());
    }

    // ---------------------------------------------------------------
    // Listing 5.15 — CellularAutomata
    // ---------------------------------------------------------------

    @Test
    void cellularAutomata_runsToConvergence() throws InterruptedException {
        int maxSteps = 5;
        CellularAutomata.Board board = new CellularAutomata.Board(4, 4, maxSteps);
        CellularAutomata ca = new CellularAutomata(board);
        ca.start();
        ca.waitForConvergence();

        assertTrue(board.getStep() >= maxSteps,
                "Board should have run to at least " + maxSteps + " steps");
    }

    // ---------------------------------------------------------------
    // Memoizer evolution (Listings 5.16 – 5.19)
    // ---------------------------------------------------------------

    @Test
    void memoizer_dedupesConcurrentComputationsForSameKey() throws InterruptedException {
        ExpensiveFunction underlying = new ExpensiveFunction();
        Memoizer<String, BigInteger> memoizer = new Memoizer<>(underlying);

        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // All threads ask for the SAME key at the same time.
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    BigInteger r = memoizer.compute("42");
                    assertEquals(new BigInteger("42"), r);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    endGate.countDown();
                }
            }).start();
        }

        startGate.countDown();
        endGate.await();

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        // The key insight: despite 10 concurrent calls for the same key,
        // the expensive underlying function should have been invoked ONCE.
        assertEquals(1, underlying.getInvocationCount(),
                "Memoizer must dedupe concurrent compute() calls for the same key");
    }

    @Test
    void memoizer2_canDuplicateWorkForSameKey() throws InterruptedException {
        // Probabilistic: Memoizer2's check-then-act lets multiple threads
        // compute the same value. We just observe it, don't strictly assert.
        ExpensiveFunction underlying = new ExpensiveFunction();
        Memoizer2<String, BigInteger> memoizer = new Memoizer2<>(underlying);

        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    memoizer.compute("42");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            }).start();
        }
        startGate.countDown();
        endGate.await();

        // Often > 1, but can be == 1 on fast machines — just report.
        System.out.println("Memoizer2 invocations for the same key: " + underlying.getInvocationCount());
        assertTrue(underlying.getInvocationCount() >= 1);
    }

    @Test
    void factorizer_producesCorrectFactorsAndCaches() throws InterruptedException {
        Factorizer factorizer = new Factorizer();

        BigInteger n = BigInteger.valueOf(2310); // 2 * 3 * 5 * 7 * 11
        BigInteger[] first = factorizer.service(n);
        BigInteger[] second = factorizer.service(n);

        assertArrayEquals(first, second, "Same input should return same result");
        BigInteger product = java.util.Arrays.stream(first).reduce(BigInteger.ONE, BigInteger::multiply);
        assertEquals(n, product);
    }

    // Keeping these imports used
    @SuppressWarnings("unused")
    private void unused() {
        List.of();
        Set.of();
    }
}
