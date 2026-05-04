package org.example.chapter05;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ModernConcurrentCollections} and
 * {@link ComputeIfAbsentMemoizer}.
 *
 * The goal of these tests is to document — and prove — which
 * collections tolerate iterate-while-modify and which don't.
 */
class ModernConcurrentCollectionsTest {

    @Test
    void arrayList_throwsCMEWhenModifiedDuringIteration() {
        assertTrue(ModernConcurrentCollections.arrayListThrowsCme(),
                "Plain ArrayList must throw ConcurrentModificationException on mid-iteration mutation");
    }

    @Test
    void synchronizedSet_stillThrowsCMEDuringIteration() {
        assertTrue(ModernConcurrentCollections.synchronizedSetStillThrowsCmeDuringIteration(),
                "Collections.synchronizedSet uses a fail-fast iterator — wrapping doesn't help here");
    }

    @Test
    void newKeySet_allowsIterationWhileMutating() throws InterruptedException {
        // If any thread threw CME, the test would fail with an exception.
        Set<Integer> result = ModernConcurrentCollections.newKeySetExample();
        assertNotNull(result);
        assertTrue(result.size() > 0);
    }

    @Test
    void concurrentDeque_iteratesWeaklyWhileMutating() throws InterruptedException {
        int seen = ModernConcurrentCollections.concurrentDequeIteratesSafely();
        assertTrue(seen > 0, "Reader should have iterated many elements without CME");
    }

    @Test
    void skipListSet_returnsElementsInSortedOrder() {
        ConcurrentSkipListSet<Integer> sorted = ModernConcurrentCollections.skipListSetExample();
        assertEquals(List.of(1, 3, 5), List.copyOf(sorted));
    }

    @Test
    void copyOnWriteIterator_seesOnlyTheSnapshotAtCreation() {
        List<Integer> snapshot = ModernConcurrentCollections.copyOnWriteSnapshot();
        assertEquals(List.of(1, 2, 3), snapshot,
                "CopyOnWriteArrayList iterator should ignore mutations after creation");
    }

    // ---------------------------------------------------------------
    // ComputeIfAbsentMemoizer (modern alternative to book Memoizer)
    // ---------------------------------------------------------------

    @Test
    void computeIfAbsentMemoizer_dedupesConcurrentCallsForSameKey() throws InterruptedException {
        ExpensiveFunction underlying = new ExpensiveFunction();
        ComputeIfAbsentMemoizer<String, BigInteger> memoizer = new ComputeIfAbsentMemoizer<>(underlying);

        int threads = 10;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    BigInteger r = memoizer.compute("123");
                    assertEquals(new BigInteger("123"), r);
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
        assertEquals(1, underlying.getInvocationCount(),
                "computeIfAbsent should dedupe concurrent calls for the same key");
    }

    @Test
    void computeIfAbsentMemoizer_differentKeysCanRunConcurrently() throws InterruptedException {
        ExpensiveFunction underlying = new ExpensiveFunction();
        ComputeIfAbsentMemoizer<String, BigInteger> memoizer = new ComputeIfAbsentMemoizer<>(underlying);

        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int n = i;
            new Thread(() -> {
                try {
                    startGate.await();
                    memoizer.compute(String.valueOf(n));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            }).start();
        }
        startGate.countDown();
        endGate.await();

        assertEquals(threads, underlying.getInvocationCount(),
                "One invocation per distinct key");
    }
}
