package org.example.chapter05;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Listing 5.6 - Iteration Hidden within String Concatenation. Don't Do This.
 *
 * The `set` field is guarded by `this` — add/remove are synchronized.
 * But `addTenThings()` contains a SUBTLE BUG: the string concatenation
 * in the println invokes `set.toString()`, which iterates the set.
 * That iteration happens WITHOUT the lock held, because we're outside
 * a synchronized method.
 *
 * If another thread calls add/remove during that toString-iteration,
 * we get ConcurrentModificationException.
 *
 * Hidden iterators also lurk in: hashCode/equals (if the collection is
 * used as a key), containsAll/removeAll/retainAll, and collection copy
 * constructors.
 *
 * The real lesson: the greater the distance between a collection and
 * the synchronization that guards it, the easier it is to forget.
 * Encapsulating the collection behind synchronizedSet would eliminate
 * this class of bug.
 */
public class HiddenIterator {

    private final Set<Integer> set = new HashSet<>();

    public synchronized void add(Integer i) {
        set.add(i);
    }

    public synchronized void remove(Integer i) {
        set.remove(i);
    }

    public void addTenThings() {
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            add(r.nextInt());
        }
        // BUG: toString() iterates the set without the lock
        System.out.println("DEBUG: added ten elements to " + set);
    }
}
