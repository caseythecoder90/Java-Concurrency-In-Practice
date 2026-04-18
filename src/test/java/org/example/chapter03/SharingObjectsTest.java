package org.example.chapter03;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the sharing-objects concepts from Chapter 3:
 *
 * - Unsafe state escape via returned array reference
 * - ThreadLocal gives each thread its own connection
 * - Immutable ThreeStooges is safe to share freely
 * - VolatileCachedFactorizer produces correct results under contention
 * - Holder with a safely-published reference behaves correctly
 */
class SharingObjectsTest {

    private static final int THREADS = 10;
    private static final int OPS_PER_THREAD = 1_000;

    // ---------------------------------------------------------------
    // Listing 3.6 — UnsafeStates lets internal state escape
    // ---------------------------------------------------------------

    @Test
    void unsafeStates_allowsInternalStateMutation() {
        UnsafeStates states = new UnsafeStates();
        String[] returned = states.getStates();
        String original = returned[0];
        returned[0] = "MUTATED";

        // The internal array was mutated through the returned reference
        assertEquals("MUTATED", states.getStates()[0],
                "Returned array is the same instance as internal state — escape!");
        assertNotEquals(original, states.getStates()[0]);
    }

    // ---------------------------------------------------------------
    // Listing 3.10 — ThreadLocal confines one connection per thread
    // ---------------------------------------------------------------

    @Test
    void connectionHolder_givesEachThreadItsOwnInstance() throws InterruptedException {
        int threads = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        ConnectionHolder.Connection[] connections = new ConnectionHolder.Connection[threads];

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    connections[idx] = ConnectionHolder.getConnection();
                    // Second call in same thread should return the SAME instance
                    assertSame(connections[idx], ConnectionHolder.getConnection(),
                            "ThreadLocal should return same instance within a thread");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // Every thread should have gotten a distinct Connection
        for (int i = 0; i < threads; i++) {
            for (int j = i + 1; j < threads; j++) {
                assertNotSame(connections[i], connections[j],
                        "Different threads should have different ThreadLocal instances");
            }
        }
    }

    // ---------------------------------------------------------------
    // Listing 3.11 — ThreeStooges is immutable and thread-safe
    // ---------------------------------------------------------------

    @Test
    void threeStooges_isImmutableAndThreadSafe() throws InterruptedException {
        ThreeStooges stooges = new ThreeStooges();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        if (!stooges.isStooge("Moe") || !stooges.isStooge("Larry") || !stooges.isStooge("Curly")) {
                            errors.incrementAndGet();
                        }
                        if (stooges.isStooge("Shemp")) {
                            errors.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        assertEquals(0, errors.get(), "Immutable ThreeStooges should never produce wrong results");
    }

    // ---------------------------------------------------------------
    // Listing 3.13 — VolatileCachedFactorizer under contention
    // ---------------------------------------------------------------

    @Test
    void volatileCachedFactorizer_producesCorrectFactorsUnderContention() throws InterruptedException {
        VolatileCachedFactorizer factorizer = new VolatileCachedFactorizer();
        BigInteger[] testNumbers = {
                BigInteger.valueOf(12),    // 2, 2, 3
                BigInteger.valueOf(17),    // 17 (prime)
                BigInteger.valueOf(100),   // 2, 2, 5, 5
                BigInteger.valueOf(2310),  // 2, 3, 5, 7, 11
        };

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        AtomicReference<AssertionError> failure = new AtomicReference<>();

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        BigInteger num = testNumbers[j % testNumbers.length];
                        List<BigInteger> factors = factorizer.service(num);
                        BigInteger product = factors.stream()
                                .reduce(BigInteger.ONE, BigInteger::multiply);
                        assertEquals(num, product,
                                "Factors of " + num + " should multiply back: " + factors);
                    }
                } catch (AssertionError e) {
                    failure.compareAndSet(null, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        if (failure.get() != null) {
            throw failure.get();
        }
    }

    // ---------------------------------------------------------------
    // Listing 3.15 — Holder behaves correctly when safely published
    // ---------------------------------------------------------------

    @Test
    void holder_withSafePublication_passesSanityCheck() throws InterruptedException {
        // Publish via a volatile field (safe publication idiom #2)
        SafePublisher publisher = new SafePublisher();
        publisher.publishVolatile();

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < OPS_PER_THREAD; j++) {
                        try {
                            publisher.volatileHolder.assertSanity();
                        } catch (AssertionError e) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        assertEquals(0, failures.get(),
                "Safely published Holder must never fail its sanity check");
    }
}
