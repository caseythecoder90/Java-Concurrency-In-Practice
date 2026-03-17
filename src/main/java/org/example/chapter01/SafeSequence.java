package org.example.chapter01;

/**
 * Listing 1.2 - Thread-safe Sequence Generator.
 *
 * By synchronizing getNext(), we ensure that only one thread at a time
 * can execute the read-modify-write operation on value. This eliminates
 * the race condition present in {@link UnsafeSequence}.
 */
public class SafeSequence {

    private int value;

    public synchronized int getNext() {
        return value++;
    }
}
