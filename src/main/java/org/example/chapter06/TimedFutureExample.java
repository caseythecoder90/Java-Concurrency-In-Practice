package org.example.chapter06;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Listing 6.16 - Per-task time-bounded Future.get usage.
 *
 * The contract: call Future.get(timeout, unit). If it throws
 * TimeoutException, return a default — but ALWAYS cancel the underlying
 * task in finally, otherwise it keeps running and consumes resources for
 * a result no one will read.
 *
 * Cancellation is cooperative: the task must respect interruption.
 * Tasks that block in interruptible methods (Thread.sleep, queue.take,
 * etc.) will return immediately; tasks doing pure-CPU work must check
 * Thread.interrupted() periodically.
 */
public final class TimedFutureExample {

    private TimedFutureExample() {}

    public static <V> V getOrDefault(ExecutorService exec,
                                      Callable<V> task,
                                      long timeoutMillis,
                                      V defaultValue) throws InterruptedException {
        Future<V> f = exec.submit(task);
        try {
            return f.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            return defaultValue;
        } catch (ExecutionException ee) {
            return defaultValue;
        } finally {
            f.cancel(true);
        }
    }
}
