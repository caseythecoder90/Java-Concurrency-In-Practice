package org.example.chapter05;

import java.util.HashMap;
import java.util.Map;

/**
 * Listing 5.16 - Initial Cache Attempt Using HashMap and Synchronization.
 *
 * HashMap is not thread-safe, so the conservative fix is to synchronize
 * the WHOLE compute method. It works, but:
 *
 *   - Only one thread at a time can be inside compute.
 *   - A slow computation blocks every caller — even those asking for
 *     a DIFFERENT key.
 *   - In the worst case, Memoizer1 is SLOWER than no cache at all.
 *
 * Correct but terrible. See Memoizer2 through Memoizer for the fixes.
 */
public class Memoizer1<A, V> implements Computable<A, V> {

    private final Map<A, V> cache = new HashMap<>();
    private final Computable<A, V> c;

    public Memoizer1(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public synchronized V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}
