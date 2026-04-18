package org.example.chapter04;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listing 4.10 - Number Range Class that does Not Sufficiently Protect
 * Its Invariants. Don't Do This.
 *
 * INVARIANT: lower <= upper
 *
 * Although `lower` and `upper` are each thread-safe (AtomicInteger),
 * the COMPOSITE is not thread-safe. setLower and setUpper are both
 * check-then-act sequences without sufficient locking:
 *
 *   Range = (0, 10)
 *   Thread A: setLower(5) — checks 5 > upper.get() (false), passes
 *   Thread B: setUpper(4) — checks 4 < lower.get() (false), passes
 *   Thread A: lower.set(5)
 *   Thread B: upper.set(4)
 *   Range is now (5, 4) — INVALID STATE.
 *
 * Rule: when multiple state variables participate in an invariant,
 * per-variable atomicity isn't enough. The compound action must be
 * atomic — e.g. guard both with the same lock.
 */
public class NumberRange {

    private final AtomicInteger lower = new AtomicInteger(0);
    private final AtomicInteger upper = new AtomicInteger(0);

    public void setLower(int i) {
        // UNSAFE check-then-act
        if (i > upper.get()) {
            throw new IllegalArgumentException("can't set lower to " + i + " > upper");
        }
        lower.set(i);
    }

    public void setUpper(int i) {
        // UNSAFE check-then-act
        if (i < lower.get()) {
            throw new IllegalArgumentException("can't set upper to " + i + " < lower");
        }
        upper.set(i);
    }

    public boolean isInRange(int i) {
        return i >= lower.get() && i <= upper.get();
    }

    public int getLower() {
        return lower.get();
    }

    public int getUpper() {
        return upper.get();
    }
}
