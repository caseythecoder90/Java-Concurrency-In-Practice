package org.example.chapter01;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the race condition in UnsafeSequence
 * and the correctness of SafeSequence.
 *
 * UnsafeSequence: multiple threads calling getNext() can produce
 * duplicate values because value++ is not atomic (read-modify-write).
 *
 * SafeSequence: the synchronized keyword ensures mutual exclusion,
 * so every call to getNext() returns a unique value.
 */
class SequenceTest {

    private static final int THREADS = 10;
    private static final int INCREMENTS_PER_THREAD = 1_000;
    private static final int TOTAL = THREADS * INCREMENTS_PER_THREAD;

    @Test
    void unsafeSequence_producesDuplicatesUnderContention() throws InterruptedException {
        UnsafeSequence seq = new UnsafeSequence();
        Set<Integer> allValues = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await(); // all threads start at the same time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                    allValues.add(seq.getNext());
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown(); // release all threads
        endGate.await();       // wait for all to finish

        // If all values were unique, set size would equal TOTAL.
        // With a race condition, duplicates cause fewer unique values.
        // This test may occasionally pass (race conditions are probabilistic),
        // but with 10 threads x 1000 increments it almost always shows duplicates.
        System.out.println("UnsafeSequence: " + allValues.size() + " unique values out of " + TOTAL);
        assertTrue(allValues.size() < TOTAL,
                "Expected duplicates from UnsafeSequence due to race condition, but got all unique values. " +
                "This can occasionally happen — rerun the test.");
    }

    @Test
    void safeSequence_alwaysProducesUniqueValues() throws InterruptedException {
        SafeSequence seq = new SafeSequence();
        Set<Integer> allValues = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                    allValues.add(seq.getNext());
                }
                endGate.countDown();
            });
            t.start();
        }

        startGate.countDown();
        endGate.await();

        // Synchronized guarantees every value is unique
        System.out.println("SafeSequence: " + allValues.size() + " unique values out of " + TOTAL);
        assertEquals(TOTAL, allValues.size(),
                "SafeSequence should produce exactly " + TOTAL + " unique values");
    }
}
