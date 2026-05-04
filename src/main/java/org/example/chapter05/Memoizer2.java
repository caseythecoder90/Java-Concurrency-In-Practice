package org.example.chapter05;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listing 5.17 - Replacing HashMap with ConcurrentHashMap.
 *
 * Drops the method-level `synchronized`; multiple threads can now
 * execute compute concurrently. Big improvement over Memoizer1.
 *
 * Window of vulnerability: two threads asking for the SAME key at the
 * same time both see `cache.get(arg) == null`, both run c.compute,
 * and both store the result. Duplicate work.
 *
 * For pure memoization this is inefficient but safe. For caches meant
 * to provide ONCE-AND-ONLY-ONCE initialization (e.g. object caches),
 * this would be a SAFETY bug — you'd get two distinct instances.
 */
public class Memoizer2<A, V> implements Computable<A, V> {

    private final Map<A, V> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer2(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);
        if (result == null) {
            result = c.compute(arg);
            cache.put(arg, result);
        }
        return result;
    }
}
