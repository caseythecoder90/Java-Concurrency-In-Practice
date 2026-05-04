package org.example.chapter05;

import java.util.Vector;

/**
 * Listing 5.1 - Compound Actions on a Vector that may Produce Confusing Results.
 *
 * Each method below is a check-then-act sequence over a Vector. Individually,
 * `size()`, `get(i)`, and `remove(i)` are atomic (Vector synchronizes each
 * method). But the PAIR size()+get() is NOT atomic — another thread can
 * shrink the vector between the two calls, and get(lastIndex) will then
 * throw ArrayIndexOutOfBoundsException.
 *
 * This does not mean Vector is broken — throwing AIOOBE when you ask for
 * a missing index is Vector's spec. The race is in the caller's compound
 * action.
 *
 * See {@link SafeVectorCompoundActions} for the client-side-locking fix.
 */
public class UnsafeVectorCompoundActions {

    public static Object getLast(Vector<?> list) {
        int lastIndex = list.size() - 1;
        return list.get(lastIndex);
    }

    public static void deleteLast(Vector<?> list) {
        int lastIndex = list.size() - 1;
        list.remove(lastIndex);
    }
}
