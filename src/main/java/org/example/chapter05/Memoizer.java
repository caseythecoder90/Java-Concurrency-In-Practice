package org.example.chapter05;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Listing 5.19 - Final Implementation of Memoizer.
 *
 * Closes the check-then-act window by using ConcurrentMap.putIfAbsent,
 * which atomically puts only if no value is currently mapped. Exactly
 * one FutureTask wins the race; every other caller retrieves the
 * winner's Future and waits on it.
 *
 * Handles CACHE POLLUTION: if a cached Future completes with
 * CancellationException, future lookups would inherit that failure.
 * The while loop removes the poisoned Future and retries. Similar
 * cleanup could be done for RuntimeException if retrying might succeed.
 *
 * Not addressed (but extensible):
 *   - Expiration (subclass FutureTask with a timestamp + periodic sweep)
 *   - Eviction / LRU
 */
public class Memoizer<A, V> implements Computable<A, V> {

    private final ConcurrentMap<A, Future<V>> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(final A arg) throws InterruptedException {
        while (true) {
            Future<V> f = cache.get(arg);
            if (f == null) {
                Callable<V> eval = () -> c.compute(arg);
                FutureTask<V> ft = new FutureTask<>(eval);
                f = cache.putIfAbsent(arg, ft);
                if (f == null) {
                    // We won the race — run the computation ourselves.
                    f = ft;
                    ft.run();
                }
                // else: someone else got there first; fall through and await theirs.
            }
            try {
                return f.get();
            } catch (CancellationException e) {
                // Don't leave a cancelled Future poisoning the cache
                cache.remove(arg, f);
                // loop and try again
            } catch (ExecutionException e) {
                throw LaunderThrowable.launderThrowable(e.getCause());
            }
        }
    }
}
