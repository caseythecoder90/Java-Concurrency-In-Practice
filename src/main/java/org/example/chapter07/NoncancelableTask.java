package org.example.chapter07;

import java.util.concurrent.BlockingQueue;

/**
 * Listing 7.7 - Noncancelable Task that Restores Interruption Before Exit.
 *
 * What to do when:
 *   - your method calls an interruptible blocking method,
 *   - you cannot propagate {@code InterruptedException} (the signature
 *     doesn't allow it), AND
 *   - you don't want the operation to be cancellable mid-flight.
 *
 * The trick: remember interruption locally, retry the blocking call, and
 * restore the flag in {@code finally} — NOT immediately on catch.
 * Restoring early would cause {@code take()} to throw immediately on
 * its next call (since most interruptible methods check the flag on
 * entry), creating an infinite loop.
 *
 * The result: the method always returns a Task (the operation runs to
 * completion), but it preserves interruption status so the caller can
 * still observe that the thread was interrupted.
 */
public final class NoncancelableTask {

    private NoncancelableTask() {}

    public static <T> T takeRetryingOnInterrupt(BlockingQueue<T> queue) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    interrupted = true;
                    // swallow — fall through and retry
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
