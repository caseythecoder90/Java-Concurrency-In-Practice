package org.example.chapter02;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Listing 2.4 - Servlet that Counts Requests Using AtomicLong.
 *
 * By replacing a plain long with AtomicLong, the increment becomes
 * an atomic operation using hardware compare-and-swap (CAS). This
 * eliminates the read-modify-write race condition without locks.
 *
 * AtomicLong is part of java.util.concurrent.atomic — a set of
 * classes that use lock-free, thread-safe operations on single variables.
 *
 * This works because we have only ONE piece of mutable state. If we
 * had two related variables, individual atomic operations would not
 * be sufficient to preserve invariants between them.
 */
public class CountingFactorizer {

    private final AtomicLong count = new AtomicLong(0);

    public long getCount() {
        return count.get();
    }

    public void service() {
        count.incrementAndGet(); // atomic read-modify-write via CAS
    }
}
