package org.example.chapter03;

/**
 * Listing 3.2 - Non-Thread-Safe Mutable Integer Holder.
 *
 * The value field is accessed from both get and set without
 * synchronization. Among other hazards, it is susceptible to stale
 * values: after one thread calls set, other threads calling get may
 * or may not see the update.
 */
public class MutableInteger {

    private int value;

    public int get() {
        return value;
    }

    public void set(int value) {
        this.value = value;
    }
}
