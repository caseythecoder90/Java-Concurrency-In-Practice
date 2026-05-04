package org.example.chapter05;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Listing 5.14 - Using Semaphore to Bound a Collection.
 *
 * Wraps a plain (synchronized) Set with a Semaphore whose initial
 * permit count is the desired maximum size. `add` acquires a permit
 * before adding; if the underlying add was a no-op (duplicate element),
 * release the permit immediately so capacity isn't wasted.
 *
 * The underlying Set is unaware of the bound — the Semaphore enforces
 * it entirely on the outside.
 *
 * This is the canonical pattern for building bounded blocking resource
 * pools and capped collections.
 */
public class BoundedHashSet<T> {

    private final Set<T> set;
    private final Semaphore sem;

    public BoundedHashSet(int bound) {
        this.set = Collections.synchronizedSet(new HashSet<>());
        this.sem = new Semaphore(bound);
    }

    public boolean add(T o) throws InterruptedException {
        sem.acquire();
        boolean wasAdded = false;
        try {
            wasAdded = set.add(o);
            return wasAdded;
        } finally {
            if (!wasAdded) {
                sem.release();
            }
        }
    }

    public boolean remove(Object o) {
        boolean wasRemoved = set.remove(o);
        if (wasRemoved) {
            sem.release();
        }
        return wasRemoved;
    }

    public int size() {
        return set.size();
    }
}
