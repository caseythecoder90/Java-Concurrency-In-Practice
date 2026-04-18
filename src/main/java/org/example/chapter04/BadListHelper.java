package org.example.chapter04;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Listing 4.14 - Non-Thread-Safe Attempt to Implement put-if-absent. Don't Do This.
 *
 * synchronizes on the WRONG LOCK. The synchronizedList's own
 * synchronized methods lock on the WRAPPER collection (the List
 * returned from Collections.synchronizedList), not on this helper.
 *
 * Because `putIfAbsent` synchronizes on `this` (BadListHelper) and
 * the list's own methods synchronize on the list, another thread can
 * call list.add(x) between our `contains` check and our `add` — so
 * putIfAbsent is not atomic with respect to other list operations.
 *
 * The fix is to synchronize on the list itself (see
 * {@link CorrectListHelper}) — or better yet, use composition with
 * an independent lock.
 */
public class BadListHelper<E> {

    public final List<E> list = Collections.synchronizedList(new ArrayList<>());

    public synchronized boolean putIfAbsent(E x) {
        boolean absent = !list.contains(x);
        if (absent) {
            list.add(x);
        }
        return absent;
    }
}
