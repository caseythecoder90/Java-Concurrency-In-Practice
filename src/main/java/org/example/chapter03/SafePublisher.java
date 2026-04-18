package org.example.chapter03;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Demonstrates the four safe publication idioms from Section 3.5.3.
 *
 * A properly constructed object can be safely published by:
 *   1. Initializing it from a STATIC INITIALIZER.
 *   2. Storing the reference in a VOLATILE field or AtomicReference.
 *   3. Storing the reference in a FINAL field of a properly constructed object.
 *   4. Storing the reference in a field PROPERLY GUARDED BY A LOCK.
 *
 * Thread-safe collections (Hashtable, ConcurrentMap, synchronizedMap,
 * Vector, CopyOnWriteArrayList/Set, synchronizedList/Set,
 * BlockingQueue, ConcurrentLinkedQueue) also provide safe publication
 * via their internal synchronization.
 */
public class SafePublisher {

    // (1) Static initializer — the JVM's class-init synchronization handles it.
    public static final Holder STATIC_HOLDER = new Holder(42);

    // (2) Volatile field.
    public volatile Holder volatileHolder;

    // (2) AtomicReference.
    public final AtomicReference<Holder> atomicHolder = new AtomicReference<>();

    // (3) Final field (set once in the constructor).
    public final Holder finalHolder;

    // (4) Lock-guarded field.
    private Holder guardedHolder;
    private final Object lock = new Object();

    public SafePublisher() {
        this.finalHolder = new Holder(42);
    }

    public void publishVolatile() {
        volatileHolder = new Holder(42);
    }

    public void publishAtomic() {
        atomicHolder.set(new Holder(42));
    }

    public void publishGuarded(Holder h) {
        synchronized (lock) {
            this.guardedHolder = h;
        }
    }

    public Holder readGuarded() {
        synchronized (lock) {
            return guardedHolder;
        }
    }
}
