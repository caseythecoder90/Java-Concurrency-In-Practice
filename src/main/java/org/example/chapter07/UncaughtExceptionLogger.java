package org.example.chapter07;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listing 7.25 - UncaughtExceptionHandler that Logs the Exception.
 *
 * The defensive complement to wrapping task code in try/catch: catches
 * the exceptions that escape that wrapping or come from threads outside
 * a pool worker structure. At a minimum, log them — silent thread death
 * is one of the most confusing failure modes in concurrent code.
 *
 * Set per-thread:
 *   thread.setUncaughtExceptionHandler(new UncaughtExceptionLogger());
 *
 * Set globally:
 *   Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionLogger());
 *
 * For thread pools, install via a ThreadFactory so every worker created
 * by the pool gets the handler.
 *
 * IMPORTANT: this fires only for tasks submitted with execute(). Tasks
 * submitted with submit() route exceptions through ExecutionException
 * inside Future — the handler is not called. Wire up both if you have
 * mixed submission patterns.
 */
public class UncaughtExceptionLogger implements Thread.UncaughtExceptionHandler {

    private final Logger logger;

    public UncaughtExceptionLogger() {
        this(Logger.getAnonymousLogger());
    }

    public UncaughtExceptionLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        logger.log(Level.SEVERE, "Thread terminated with exception: " + t.getName(), e);
    }
}
