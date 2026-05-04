package org.example.chapter06;

import java.util.concurrent.Executor;

/**
 * Listing 6.4 - Executor That Starts a New Thread for Each Task.
 *
 * Equivalent to writing {@code new Thread(r).start()} directly, but
 * exposed behind the Executor interface so callers don't need to know.
 * Useful as a baseline for comparing pool-based executors and as a
 * sanity check that Executor really does decouple policy from submission.
 *
 * Production code should rarely use this — it inherits all the problems
 * of unbounded thread creation.
 */
public class ThreadPerTaskExecutor implements Executor {

    @Override
    public void execute(Runnable r) {
        new Thread(r).start();
    }
}
