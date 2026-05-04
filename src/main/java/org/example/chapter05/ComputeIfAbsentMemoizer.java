package org.example.chapter05;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Modern replacement for the book's Memoizer using
 * ConcurrentHashMap.computeIfAbsent (Java 8).
 *
 * The final Memoizer (Listing 5.19) is the gold standard of the book:
 * about 30 lines of code involving Future, FutureTask, putIfAbsent,
 * cancellation cleanup, and laundering. In Java 8+, a simpler version
 * is possible:
 *
 *   cache.computeIfAbsent(arg, c::compute)
 *
 * computeIfAbsent is an ATOMIC compound operation on ConcurrentHashMap:
 *   - If the key is absent, the mapping function runs and the result
 *     is stored atomically; other threads calling computeIfAbsent for
 *     the same key will BLOCK until the first call finishes, then
 *     return the same result.
 *   - If the key is present, the stored value is returned and the
 *     function is not invoked.
 *
 * Important caveats:
 *   - The mapping function runs WITH A LOCK on that bin held. Don't
 *     do long I/O or call back into the same map with a different key
 *     that could hash to the same bin — you can deadlock.
 *   - The mapping function should be short and side-effect free.
 *   - Exceptions from the function propagate out and the key stays
 *     unmapped — no cache pollution to clean up. That is NICE.
 *
 * For heavy / long-running computations, the book's Future-based
 * Memoizer is still preferable: the computation runs outside the map's
 * internal lock, so unrelated keys don't stall behind it.
 *
 * The Computable interface declares `throws InterruptedException`,
 * which can't flow through Function — we wrap it as a RuntimeException
 * inside the lambda and unwrap on the way out.
 */
public class ComputeIfAbsentMemoizer<A, V> implements Computable<A, V> {

    private final ConcurrentHashMap<A, V> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public ComputeIfAbsentMemoizer(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(A arg) throws InterruptedException {
        try {
            return cache.computeIfAbsent(arg, key -> {
                try {
                    return c.compute(key);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WrappedInterrupted(e);
                }
            });
        } catch (WrappedInterrupted w) {
            throw (InterruptedException) w.getCause();
        }
    }

    private static final class WrappedInterrupted extends RuntimeException {
        WrappedInterrupted(InterruptedException cause) { super(cause); }
    }
}
