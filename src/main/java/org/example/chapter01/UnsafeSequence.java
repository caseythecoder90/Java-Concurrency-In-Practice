package org.example.chapter01;

/**
 * Listing 1.1 - Non-thread-safe Sequence Generator.
 *
 * The post-increment operator (value++) is NOT atomic.
 * It is actually three discrete operations:
 *   1. Read the current value
 *   2. Add one to it
 *   3. Write the new value back
 *
 * If two threads call getNext() concurrently, they can both read the same
 * value, both increment it, and both write back the same result — producing
 * duplicate sequence numbers. This is a race condition.
 */
public class UnsafeSequence {

    private int value;

    public int getNext() {
        return value++;
    }
}
