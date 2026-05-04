package org.example.chapter06;

import java.util.concurrent.Executor;

/**
 * Listing 6.5 - Executor That Executes Tasks Synchronously in the Calling
 * Thread.
 *
 * Looks like a no-op, and almost is — but the Executor abstraction
 * delivers real value: caller code says {@code exec.execute(task)} and
 * doesn't have to care whether the task is dispatched to a pool or run
 * inline. That makes this the perfect Executor for unit tests, where you
 * usually want deterministic, single-threaded execution.
 *
 * Not a useful production Executor — it gives you no concurrency at all.
 */
public class WithinThreadExecutor implements Executor {

    @Override
    public void execute(Runnable r) {
        r.run();
    }
}
