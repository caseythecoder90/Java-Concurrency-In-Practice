package org.example.chapter05;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modern concurrent collections for iterate-while-modify.
 *
 * JCIP was written in 2006 (Java 5/6 era). The rules it teaches still
 * hold, but the JDK has grown several concurrent collections that make
 * iterate-while-modify straightforward without client-side locking,
 * cloning, or CopyOnWriteArrayList's full-array copies.
 *
 * The TL;DR:
 *
 *   1. NEVER iterate an ArrayList / HashSet / HashMap / synchronized*
 *      wrapper while another thread mutates it — you'll get
 *      ConcurrentModificationException (fail-fast iterators).
 *
 *   2. DO iterate any java.util.concurrent collection freely — their
 *      iterators are either:
 *         (a) "weakly consistent" — tolerate concurrent mods, may or
 *             may not reflect them (ConcurrentHashMap, the
 *             concurrent Queue/Deque/SkipList collections), OR
 *         (b) "snapshot" — operate on an immutable copy taken when the
 *             iterator was created (CopyOnWriteArrayList/Set).
 *
 *      Neither throws ConcurrentModificationException.
 *
 * Cheat sheet for picking one:
 *
 *   Need                                    | Use
 *   ---------------------------------------+-------------------------------------------
 *   Concurrent Map                          | ConcurrentHashMap          (Java 5)
 *   Sorted concurrent Map                   | ConcurrentSkipListMap      (Java 6)
 *   Concurrent Set                          | ConcurrentHashMap.newKeySet()  (Java 8)
 *   Sorted concurrent Set                   | ConcurrentSkipListSet      (Java 6)
 *   Listener-list (rare writes, iteration)  | CopyOnWriteArrayList/Set   (Java 5)
 *   FIFO non-blocking queue                 | ConcurrentLinkedQueue      (Java 5)
 *   Double-ended non-blocking queue         | ConcurrentLinkedDeque      (Java 7)
 *   Blocking queue with direct handoff      | LinkedTransferQueue        (Java 7)
 *
 * See the demo methods below for each case.
 */
public final class ModernConcurrentCollections {

    private ModernConcurrentCollections() {}

    // ------------------------------------------------------------------
    // DON'T: plain collections throw CME when modified during iteration
    // ------------------------------------------------------------------

    /**
     * Demonstrates that a regular ArrayList throws
     * ConcurrentModificationException if modified mid-iteration —
     * even in a SINGLE thread, if you use the collection's own
     * remove/add instead of Iterator.remove.
     */
    public static boolean arrayListThrowsCme() {
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        try {
            for (Integer n : list) {
                if (n == 3) {
                    list.remove(Integer.valueOf(3));   // ← CME next iteration
                }
            }
            return false;
        } catch (java.util.ConcurrentModificationException e) {
            return true;
        }
    }

    // ------------------------------------------------------------------
    // DO: ConcurrentHashMap.newKeySet() — the modern concurrent Set
    // (Java 8)
    // ------------------------------------------------------------------

    /**
     * ConcurrentHashMap.newKeySet() gives you a first-class Set backed
     * by a ConcurrentHashMap — all the concurrency benefits of CHM
     * (lock striping, weakly-consistent iterators, no CME) as a Set.
     *
     * Prefer this over Collections.synchronizedSet(new HashSet<>())
     * or Collections.newSetFromMap(new ConcurrentHashMap<>()) — same
     * effect, cleaner API.
     */
    public static Set<Integer> newKeySetExample() throws InterruptedException {
        Set<Integer> set = ConcurrentHashMap.newKeySet();
        // Seed it
        for (int i = 0; i < 100; i++) set.add(i);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger iterations = new AtomicInteger();

        // Reader: iterate forever, count how many we see; no CME, no lock.
        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int pass = 0; pass < 50; pass++) {
                    for (Integer ignored : set) {
                        iterations.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        // Writer: mutate freely while the reader is iterating.
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 100; i < 1000; i++) {
                    set.add(i);
                    if (i % 10 == 0) set.remove(i - 50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        reader.start();
        writer.start();
        start.countDown();
        done.await();
        return set;
    }

    // ------------------------------------------------------------------
    // DO: ConcurrentLinkedQueue / ConcurrentLinkedDeque
    // Non-blocking, weakly-consistent iteration, tolerate concurrent mods
    // ------------------------------------------------------------------

    /**
     * ConcurrentLinkedDeque (Java 7) — lock-free, unbounded, double-ended.
     * Iterators are weakly consistent.
     *
     * For bounded + blocking double-ended, use LinkedBlockingDeque.
     */
    public static int concurrentDequeIteratesSafely() throws InterruptedException {
        ConcurrentLinkedDeque<Integer> deque = new ConcurrentLinkedDeque<>();
        for (int i = 0; i < 1000; i++) deque.add(i);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger seen = new AtomicInteger();

        Thread reader = new Thread(() -> {
            try {
                start.await();
                // Each pass walks the deque without CME; iteration and
                // mutation proceed simultaneously.
                for (int pass = 0; pass < 10; pass++) {
                    for (Iterator<Integer> it = deque.iterator(); it.hasNext(); ) {
                        it.next();
                        seen.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 1000; i < 2000; i++) {
                    deque.addLast(i);
                    deque.pollFirst();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });

        reader.start();
        writer.start();
        start.countDown();
        done.await();
        return seen.get();
    }

    // ------------------------------------------------------------------
    // DO: ConcurrentSkipListSet — sorted, concurrent, weakly consistent
    // ------------------------------------------------------------------

    public static ConcurrentSkipListSet<Integer> skipListSetExample() {
        ConcurrentSkipListSet<Integer> sorted = new ConcurrentSkipListSet<>();
        sorted.add(5);
        sorted.add(1);
        sorted.add(3);
        // Iteration order is sorted, iterator is weakly consistent.
        return sorted;
    }

    // ------------------------------------------------------------------
    // DO: CopyOnWriteArrayList — still the right tool for listener lists
    // ------------------------------------------------------------------

    /**
     * CopyOnWriteArrayList iterators operate on an IMMUTABLE SNAPSHOT
     * taken at iterator-creation time. The iterator sees exactly the
     * elements that were in the list at that moment — never any later
     * additions or removals, never a CME.
     *
     * Cost: every mutation allocates and copies the entire backing
     * array. Use only when iteration greatly outnumbers mutation
     * (listener lists are the textbook case).
     */
    public static List<Integer> copyOnWriteSnapshot() {
        CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>(List.of(1, 2, 3));
        List<Integer> snapshot = new ArrayList<>();
        Iterator<Integer> iterator = list.iterator();

        // Mutate after the iterator was created — snapshot won't see it.
        list.add(4);
        list.add(5);

        while (iterator.hasNext()) {
            snapshot.add(iterator.next());
        }
        return snapshot;   // [1, 2, 3] — NOT [1, 2, 3, 4, 5]
    }

    // ------------------------------------------------------------------
    // Demonstrates the legacy fail-fast trap for completeness
    // ------------------------------------------------------------------

    public static boolean synchronizedSetStillThrowsCmeDuringIteration() {
        Set<Integer> set = java.util.Collections.synchronizedSet(new HashSet<>(List.of(1, 2, 3)));
        try {
            // Even under the wrapper's lock, the underlying HashSet
            // iterator is fail-fast: modifying via set.add() mid-iteration
            // triggers CME.
            synchronized (set) {
                for (Integer n : set) {
                    if (n == 2) {
                        set.add(42);   // ← CME
                    }
                }
            }
            return false;
        } catch (java.util.ConcurrentModificationException e) {
            return true;
        }
    }

    // Keep ConcurrentLinkedQueue referenced so tests can import it naturally.
    public static ConcurrentLinkedQueue<Integer> simpleQueue() {
        return new ConcurrentLinkedQueue<>();
    }
}
