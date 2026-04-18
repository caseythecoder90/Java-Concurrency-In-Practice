package org.example.chapter03;

/**
 * Listing 3.14 - Publishing an Object without Adequate Synchronization. Don't Do This.
 *
 * Storing a reference into a public (non-volatile, non-final) field is
 * NOT safe publication. Without synchronization, another thread may:
 *   - Observe `holder` as null even after initialize() returns, or
 *   - Observe the reference but see the Holder's fields in a
 *     partially constructed state (see {@link Holder#assertSanity()}).
 *
 * Use one of the safe publication idioms instead: static initializer,
 * volatile/AtomicReference field, final field of a properly
 * constructed object, or a field guarded by a lock.
 */
public class UnsafePublisher {

    public Holder holder;

    public void initialize() {
        holder = new Holder(42);
    }
}
