package org.example.chapter06;

/**
 * Listing 5.13 (reused in chapter 6) - Coercing an Unchecked Throwable
 * to a RuntimeException.
 *
 * {@link java.util.concurrent.Future#get()} wraps everything a task
 * throws — checked, unchecked, and Error — inside ExecutionException,
 * exposing the real cause as a Throwable. That's awkward to handle
 * because a Throwable could be anything.
 *
 *   - If it is an Error, re-throw immediately. Errors are unrecoverable.
 *   - If it is a RuntimeException, return it so the caller can re-throw.
 *   - Otherwise it is a checked exception that shouldn't be here — the
 *     caller was supposed to test for and rethrow known checked
 *     exceptions BEFORE calling launderThrowable. Signal a logic error.
 */
public final class LaunderThrowable {

    private LaunderThrowable() {}

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
