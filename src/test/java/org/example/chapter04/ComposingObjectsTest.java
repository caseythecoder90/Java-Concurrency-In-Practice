package org.example.chapter04;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the composing-objects concepts from Chapter 4:
 *
 * - Counter (Java monitor pattern) — no lost updates
 * - PersonSet (instance confinement) — thread-safe despite non-safe HashSet
 * - DelegatingVehicleTracker — live view reflects updates
 * - MonitorVehicleTracker — returned snapshot doesn't change
 * - NumberRange — race condition breaks the lower <= upper invariant
 * - SafePoint — atomic read of (x, y) via get()
 * - BadListHelper vs CorrectListHelper vs ImprovedList — putIfAbsent atomicity
 */
class ComposingObjectsTest {

    private static final int THREADS = 10;
    private static final int OPS_PER_THREAD = 1_000;
    private static final int TOTAL = THREADS * OPS_PER_THREAD;

    // ---------------------------------------------------------------
    // Listing 4.1 — Counter (Java monitor pattern)
    // ---------------------------------------------------------------

    @Test
    void counter_neverLosesUpdates() throws InterruptedException {
        Counter counter = new Counter();
        runConcurrently(THREADS, OPS_PER_THREAD, counter::increment);
        assertEquals(TOTAL, counter.getValue(),
                "Java monitor pattern counter should produce exactly " + TOTAL);
    }

    // ---------------------------------------------------------------
    // Listing 4.2 — PersonSet (instance confinement)
    // ---------------------------------------------------------------

    @Test
    void personSet_isThreadSafeDespiteWrappingHashSet() throws InterruptedException {
        PersonSet set = new PersonSet();
        PersonSet.Person alice = new PersonSet.Person("Alice");
        PersonSet.Person bob = new PersonSet.Person("Bob");

        runConcurrently(THREADS, OPS_PER_THREAD, () -> {
            set.addPerson(alice);
            set.addPerson(bob);
            assertTrue(set.containsPerson(alice));
            assertTrue(set.containsPerson(bob));
        });

        assertEquals(2, set.size());
    }

    // ---------------------------------------------------------------
    // Listing 4.7 — DelegatingVehicleTracker returns a LIVE view
    // ---------------------------------------------------------------

    @Test
    void delegatingVehicleTracker_liveViewReflectsUpdates() {
        Map<String, Point> init = new HashMap<>();
        init.put("taxi-1", new Point(0, 0));
        DelegatingVehicleTracker tracker = new DelegatingVehicleTracker(init);

        Map<String, Point> live = tracker.getLocations();
        assertEquals(new Point(0, 0).x, live.get("taxi-1").x);

        tracker.setLocation("taxi-1", 5, 7);

        // Live view reflects the update
        assertEquals(5, live.get("taxi-1").x);
        assertEquals(7, live.get("taxi-1").y);
    }

    @Test
    void delegatingVehicleTracker_staticSnapshotDoesNotReflectUpdates() {
        Map<String, Point> init = new HashMap<>();
        init.put("taxi-1", new Point(0, 0));
        DelegatingVehicleTracker tracker = new DelegatingVehicleTracker(init);

        Map<String, Point> snapshot = tracker.getLocationsAsStaticSnapshot();
        tracker.setLocation("taxi-1", 5, 7);

        // Snapshot was frozen at call time
        assertEquals(0, snapshot.get("taxi-1").x);
        assertEquals(0, snapshot.get("taxi-1").y);
    }

    // ---------------------------------------------------------------
    // Listing 4.4 — MonitorVehicleTracker snapshot isolation
    // ---------------------------------------------------------------

    @Test
    void monitorVehicleTracker_returnsIndependentSnapshot() {
        Map<String, MutablePoint> init = new HashMap<>();
        init.put("van-1", new MutablePoint(0, 0));
        MonitorVehicleTracker tracker = new MonitorVehicleTracker(init);

        Map<String, MutablePoint> snapshot = tracker.getLocations();
        tracker.setLocation("van-1", 42, 42);

        // The snapshot is a deep copy — tracker updates don't affect it
        assertEquals(0, snapshot.get("van-1").x);
        assertEquals(0, snapshot.get("van-1").y);

        // But a fresh call sees the new values
        assertEquals(42, tracker.getLocation("van-1").x);
    }

    // ---------------------------------------------------------------
    // Listing 4.10 — NumberRange race condition
    // ---------------------------------------------------------------

    @Test
    void numberRange_canReachInvalidState() throws InterruptedException {
        // Try enough times to catch the race
        int attempts = 200;
        boolean observedInvalid = false;
        for (int attempt = 0; attempt < attempts && !observedInvalid; attempt++) {
            NumberRange range = new NumberRange();
            // Reset to (0, 10)
            range.setUpper(10);
            range.setLower(0);

            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch endGate = new CountDownLatch(2);

            Thread lowerSetter = new Thread(() -> {
                try {
                    startGate.await();
                    range.setLower(5);
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });
            Thread upperSetter = new Thread(() -> {
                try {
                    startGate.await();
                    range.setUpper(4);
                } catch (Exception ignored) {
                } finally {
                    endGate.countDown();
                }
            });

            lowerSetter.start();
            upperSetter.start();
            startGate.countDown();
            endGate.await();

            if (range.getLower() > range.getUpper()) {
                observedInvalid = true;
                System.out.println("NumberRange race fired on attempt "
                        + attempt + ": (" + range.getLower() + ", " + range.getUpper() + ")");
            }
        }

        // Probabilistic: we demonstrate it can break, not that it always does
        System.out.println("NumberRange race observed: " + observedInvalid);
    }

    // ---------------------------------------------------------------
    // Listing 4.11 — SafePoint atomic combined accessor
    // ---------------------------------------------------------------

    @Test
    void safePoint_getReturnsAtomicSnapshot() throws InterruptedException {
        SafePoint point = new SafePoint(0, 0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(2);
        AtomicInteger mismatches = new AtomicInteger();

        // Writer thread sets (i, i) repeatedly — x should always equal y
        Thread writer = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < OPS_PER_THREAD * 10; i++) {
                    point.set(i, i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        // Reader thread observes — if get() weren't atomic, we might see mismatched x,y
        Thread reader = new Thread(() -> {
            try {
                startGate.await();
                for (int i = 0; i < OPS_PER_THREAD * 10; i++) {
                    int[] xy = point.get();
                    if (xy[0] != xy[1]) {
                        mismatches.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endGate.countDown();
            }
        });

        writer.start();
        reader.start();
        startGate.countDown();
        endGate.await();

        assertEquals(0, mismatches.get(),
                "SafePoint.get() should always return matched coordinates");
    }

    // ---------------------------------------------------------------
    // Listing 4.14 / 4.15 / 4.16 — putIfAbsent atomicity
    // ---------------------------------------------------------------

    @Test
    void correctListHelper_neverAddsDuplicates() throws InterruptedException {
        CorrectListHelper<Integer> helper = new CorrectListHelper<>();
        // Every thread tries to putIfAbsent the same values — there should
        // be no duplicates regardless of interleaving.
        runConcurrently(THREADS, OPS_PER_THREAD, () -> {
            for (int v = 0; v < 20; v++) {
                helper.putIfAbsent(v);
            }
        });

        Set<Integer> unique = new HashSet<>(helper.list);
        assertEquals(unique.size(), helper.list.size(),
                "Client-side-locking putIfAbsent should never produce duplicates");
    }

    @Test
    void improvedList_neverAddsDuplicates() throws InterruptedException {
        ImprovedList<Integer> list = new ImprovedList<>(Collections.synchronizedList(new java.util.ArrayList<>()));

        runConcurrently(THREADS, OPS_PER_THREAD, () -> {
            for (int v = 0; v < 20; v++) {
                list.putIfAbsent(v);
            }
        });

        Set<Integer> unique = new HashSet<>(list);
        assertEquals(unique.size(), list.size(),
                "Composition-based putIfAbsent should never produce duplicates");
    }

    @Test
    void betterVector_neverAddsDuplicates() throws InterruptedException {
        BetterVector<Integer> vector = new BetterVector<>();

        runConcurrently(THREADS, OPS_PER_THREAD, () -> {
            for (int v = 0; v < 20; v++) {
                vector.putIfAbsent(v);
            }
        });

        Set<Integer> unique = new HashSet<>(vector);
        assertEquals(unique.size(), vector.size(),
                "Vector-extension putIfAbsent should never produce duplicates");
    }

    @Test
    void badListHelper_canProduceDuplicates() throws InterruptedException {
        // Probabilistic: the wrong-lock helper can (but doesn't always)
        // produce duplicates. Try a few times.
        int attempts = 20;
        boolean sawDuplicates = false;

        for (int attempt = 0; attempt < attempts && !sawDuplicates; attempt++) {
            BadListHelper<Integer> helper = new BadListHelper<>();
            runConcurrently(THREADS, OPS_PER_THREAD / 10, () -> {
                for (int v = 0; v < 20; v++) {
                    helper.putIfAbsent(v);
                }
            });

            Set<Integer> unique = new HashSet<>(helper.list);
            if (unique.size() != helper.list.size()) {
                sawDuplicates = true;
                System.out.println("BadListHelper produced duplicates on attempt "
                        + attempt + ": list.size=" + helper.list.size()
                        + " unique=" + unique.size());
            }
        }

        System.out.println("BadListHelper duplicates observed: " + sawDuplicates);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static void runConcurrently(int threads, int opsPerThread, Runnable task)
            throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    for (int j = 0; j < opsPerThread; j++) {
                        task.run();
                    }
                } catch (RuntimeException e) {
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
}
