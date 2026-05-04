package org.example.chapter05;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Listing 5.18 - Memoizing Wrapper Using FutureTask.
 *
 * Key insight: cache Future<V> instead of V. A cached Future represents
 * an IN-PROGRESS (or completed) computation. Late-arriving threads call
 * Future.get() on the existing Future and wait for that single shared
 * computation to finish.
 *
 * This eliminates most duplicate work but has a SMALL remaining window
 * of vulnerability: the get()-then-put() sequence on the ConcurrentMap
 * is still a non-atomic check-then-act. Two threads can both see
 * `f == null`, both create a FutureTask, both put, and both run the
 * computation.
 *
 * See Memoizer for the final fix using putIfAbsent.
 */
public class Memoizer3<A, V> implements Computable<A, V> {

    private final Map<A, Future<V>> cache = new ConcurrentHashMap<>();
    private final Computable<A, V> c;

    public Memoizer3(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(final A arg) throws InterruptedException {
        Future<V> f = cache.get(arg);
        if (f == null) {
            Callable<V> eval = () -> c.compute(arg);
            FutureTask<V> ft = new FutureTask<>(eval);
            f = ft;
            cache.put(arg, ft);   // ← check-then-act window still open
            ft.run();             // compute happens here
        }
        try {
            return f.get();
        } catch (ExecutionException e) {
            throw LaunderThrowable.launderThrowable(e.getCause());
        }
    }
}
