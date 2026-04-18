package org.example.chapter03;

/**
 * Listing 3.3 - Thread-Safe Mutable Integer Holder.
 *
 * Synchronizing BOTH get and set on the same lock provides:
 *   1. Mutual exclusion — no torn reads/writes.
 *   2. Memory visibility — readers see the most recent write.
 *
 * Synchronizing only the setter would not be sufficient: readers
 * without the lock would still be able to see stale values.
 */
public class SynchronizedInteger {

    private int value;

    public synchronized int get() {
        return value;
    }

    public synchronized void set(int value) {
        this.value = value;
    }
}
