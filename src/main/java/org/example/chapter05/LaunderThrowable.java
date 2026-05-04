package org.example.chapter05;

/**
 * Listing 5.13 - Coercing an Unchecked Throwable to a RuntimeException.
 *
 * Future.get() wraps everything a task throws — checked, unchecked, and
 * Error — inside ExecutionException, exposing the real cause as a
 * Throwable. That's awkward to handle, because a Throwable might be
 * anything.
 *
 * Pattern:
 *   - If it's an Error, re-throw immediately. Errors are unrecoverable.
 *   - If it's a RuntimeException, return it so the caller can re-throw.
 *   - Otherwise it's a checked exception that shouldn't be here — the
 *     caller was supposed to test for and rethrow known checked
 *     exceptions BEFORE calling launderThrowable. Signal a logic error.
 */
public final class LaunderThrowable {

    private LaunderThrowable() {}

    /**
     * If the Throwable is an Error, throw it; if it is a RuntimeException,
     * return it; otherwise throw IllegalStateException (logic error — the
     * caller should have handled checked exceptions already).
     */
    public static RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException re) {
            return re;
        } else if (t instanceof Error err) {
            throw err;
        } else {
            throw new IllegalStateException("Not unchecked", t);
        }
    }
}
