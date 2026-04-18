package org.example.chapter04;

import java.util.Vector;

/**
 * Listing 4.13 - Extending Vector to have a putIfAbsent Method.
 *
 * Adds a compound atomic operation (putIfAbsent) to an existing
 * thread-safe class by EXTENSION. This works because Vector's
 * synchronization policy is well-defined (synchronize on `this` for
 * every method), so the subclass's synchronized method acquires the
 * same lock Vector uses internally.
 *
 * Caveat: extension is more fragile than adding the method directly
 * to the source class — the synchronization policy is now spread
 * across multiple files. If the base class ever changed its locking
 * policy, the subclass would break silently. (Not an issue for
 * Vector, whose policy is fixed by specification.)
 */
public class BetterVector<E> extends Vector<E> {

    public synchronized boolean putIfAbsent(E x) {
        boolean absent = !contains(x);
        if (absent) {
            add(x);
        }
        return absent;
    }
}
