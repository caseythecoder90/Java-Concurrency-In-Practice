package org.example.chapter05;

import java.util.Vector;

/**
 * Listing 5.2 - Compound Actions on Vector Using Client-side Locking.
 *
 * The synchronized wrapper/Vector classes commit to a synchronization
 * policy that supports client-side locking: every public method locks
 * on the collection itself. So we can make our own compound operations
 * atomic by acquiring that same lock.
 *
 * Acquiring `list`'s intrinsic lock prevents any other method from
 * running while we hold it — which is precisely why size() and get()
 * now execute as one atomic unit.
 *
 * This works only because Vector's locking protocol is part of its
 * spec. Don't try this blindly against arbitrary "thread-safe" classes
 * — see Section 4.4.
 */
public class SafeVectorCompoundActions {

    public static Object getLast(Vector<?> list) {
        synchronized (list) {
            int lastIndex = list.size() - 1;
            return list.get(lastIndex);
        }
    }

    public static void deleteLast(Vector<?> list) {
        synchronized (list) {
            int lastIndex = list.size() - 1;
            list.remove(lastIndex);
        }
    }
}
