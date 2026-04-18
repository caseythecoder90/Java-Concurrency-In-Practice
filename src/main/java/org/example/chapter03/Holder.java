package org.example.chapter03;

/**
 * Listing 3.15 - Class at Risk of Failure if Not Properly Published.
 *
 * Holder itself is a simple class with a single int field. But if it's
 * published UNSAFELY — e.g. assigned to a non-volatile public field
 * without synchronization — another thread may observe:
 *   1. A stale value for the `holder` reference (null or earlier), OR
 *   2. The reference but a partially constructed object: `n` reads as
 *      its default value (0) on the first read and the constructed
 *      value on the second.
 *
 * The `if (n != n)` check really can fire under improper publication,
 * because the Object constructor first writes the default 0 to `n`
 * before this class's constructor assigns the real value; threads
 * without synchronization may see writes out of order.
 *
 * The fix is either:
 *   - Make the field final (immutability gives initialization safety), OR
 *   - Safely publish (static init, volatile/AtomicReference, final
 *     field of a properly constructed object, or lock-guarded field).
 */
public class Holder {

    private int n;

    public Holder(int n) {
        this.n = n;
    }

    public void assertSanity() {
        if (n != n) {
            throw new AssertionError("This statement is false.");
        }
    }
}
