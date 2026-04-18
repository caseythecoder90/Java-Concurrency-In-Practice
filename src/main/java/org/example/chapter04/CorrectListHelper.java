package org.example.chapter04;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Listing 4.15 - Implementing put-if-absent with Client-side Locking.
 *
 * Acquires the SAME lock the synchronized-wrapper list uses to guard
 * its state (the wrapper's intrinsic lock). Because the wrapper's
 * methods all synchronize on itself, locking on `list` makes our
 * compound check-then-act atomic with respect to those methods.
 *
 * This only works because Collections.synchronizedList explicitly
 * documents that it supports client-side locking on the wrapper's
 * intrinsic lock. Outside classes that make such a commitment,
 * client-side locking is a guess.
 *
 * Fragile: the locking code lives in a class (CorrectListHelper) that
 * has no structural relationship to the list. Composition
 * ({@link ImprovedList}) is usually a better choice.
 */
public class CorrectListHelper<E> {

    public final List<E> list = Collections.synchronizedList(new ArrayList<>());

    public boolean putIfAbsent(E x) {
        synchronized (list) { // client-side locking: use the wrapper's lock
            boolean absent = !list.contains(x);
            if (absent) {
                list.add(x);
            }
            return absent;
        }
    }
}
