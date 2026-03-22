package org.example.chapter02;

/**
 * Listing 2.2 - Servlet that Counts Requests without Synchronization.
 *
 * Adding a single mutable field (count) breaks thread safety.
 * The increment operator (++count) is a read-modify-write compound
 * action that is NOT atomic. Two threads can:
 *   1. Both read the same value
 *   2. Both increment it
 *   3. Both write back the same result
 * This is a race condition — updates are lost.
 */
public class UnsafeCountingFactorizer {

    private long count;

    public long getCount() {
        return count;
    }

    public void service() {
        ++count; // NOT atomic — read-modify-write race condition
    }
}
