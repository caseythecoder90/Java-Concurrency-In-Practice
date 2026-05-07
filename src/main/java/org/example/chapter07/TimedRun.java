package org.example.chapter07;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.example.chapter07.LaunderThrowable.launderThrowable;

/**
 * Listing 7.10 - Cancelling a Task Using Future.
 *
 * The right way to enforce a deadline on a task. Submit it, wait with a
 * timeout, and ALWAYS call {@code Future.cancel(true)} in {@code finally}.
 *
 *   - If the task finished normally before the timeout: cancel is a no-op.
 *   - If we hit TimeoutException: cancel interrupts the running task.
 *   - If the task threw: launder the cause and rethrow.
 *
 * Earlier (rejected) attempts in the chapter:
 *   - Listing 7.8: schedule an interrupt on the CALLER'S thread. Disastrous
 *     if the task finishes early — interrupt fires after timedRun returns,
 *     hitting whatever is running next.
 *   - Listing 7.9: dedicated thread + timed join. Better, but join doesn't
 *     distinguish "completed" from "timed out."
 *
 * Future-based timed-run is the canonical pattern: it uses the existing
 * primitives correctly and avoids both bugs.
 */
public final class TimedRun {

    private TimedRun() {}

    public static void timedRun(ExecutorService taskExec,
                                Runnable r,
                                long timeout,
                                TimeUnit unit) throws InterruptedException {
        Future<?> task = taskExec.submit(r);
        try {
            task.get(timeout, unit);
        } catch (TimeoutException e) {
            // task will be cancelled below
        } catch (ExecutionException e) {
            throw launderThrowable(e.getCause());
        } finally {
            // Harmless if task already completed.
            task.cancel(true);
        }
    }
}
