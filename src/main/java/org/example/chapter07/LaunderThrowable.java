package org.example.chapter07;

/**
 * Reuse of Listing 5.13 / chapter 6 — coerces an unchecked {@link Throwable}
 * to a {@link RuntimeException}.
 *
 * {@link java.util.concurrent.Future#get()} wraps everything a task throws
 * inside ExecutionException. When unwrapping, you usually have something
 * unchecked, but the type-system doesn't know that. This helper:
 *
 *   - Re-throws Errors as-is (unrecoverable).
 *   - Returns RuntimeExceptions as-is so the caller can re-throw.
 *   - Throws IllegalStateException for an unexpected checked exception —
 *     "this shouldn't have been here, you forgot to handle it explicitly."
 *
 * Always handle known checked exceptions BEFORE calling launderThrowable;
 * this is the catch-all for the genuinely unexpected.
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
