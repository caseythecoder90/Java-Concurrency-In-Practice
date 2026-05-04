package org.example.chapter05;

/**
 * Listing 5.16 (part) - A function from input type A to result type V.
 *
 * The interface the Memoizer family wraps. Implementations do the
 * actual (typically expensive) work; Memoizer caches the results.
 */
public interface Computable<A, V> {
    V compute(A arg) throws InterruptedException;
}
