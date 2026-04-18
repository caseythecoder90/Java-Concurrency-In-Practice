package org.example.chapter04;

/**
 * Listing 4.1 - Simple Thread-safe Counter Using the Java Monitor Pattern.
 *
 * A textbook example of the Java monitor pattern:
 *   - A single piece of encapsulated state (`value`).
 *   - Guarded by the object's own intrinsic lock (@GuardedBy("this")).
 *   - All access goes through synchronized methods.
 *
 * Invariants:
 *   - value >= 0 (i.e. `value` must not overflow past Long.MAX_VALUE).
 * Post-condition on increment():
 *   - The next valid state is `current + 1`.
 * This is why increment() must hold the lock for the entire compound
 * action (check-then-act).
 */
public final class Counter {

    private long value = 0;

    public synchronized long getValue() {
        return value;
    }

    public synchronized long increment() {
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("counter overflow");
        }
        return ++value;
    }
}
