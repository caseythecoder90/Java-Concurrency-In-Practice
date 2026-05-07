package org.example.chapter07;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Listing 7.16 - Logging Service that Uses an ExecutorService.
 *
 * Same producer-consumer logger as {@link LogService}, but the worker
 * thread, queue, and lifecycle are delegated to a single-thread
 * ExecutorService. The reservation-counter dance disappears — shutdown
 * semantics come from {@code ExecutorService.shutdown}:
 *
 *   - {@code shutdown()} — stop accepting new log calls (RejectedExecutionException)
 *   - already-queued messages still get written
 *   - {@code awaitTermination} blocks until they're done
 *   - finally close the writer
 *
 * Don't reinvent producer-consumer plumbing. This is the production form.
 */
public class LogServiceExec {

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final PrintWriter writer;

    public LogServiceExec(Writer writer) {
        this.writer = new PrintWriter(writer, true);
    }

    public void start() {
        // Nothing to do — the executor is already running.
    }

    public void stop(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            exec.shutdown();
            exec.awaitTermination(timeout, unit);
        } finally {
            writer.close();
        }
    }

    public void log(String msg) {
        try {
            exec.execute(() -> writer.println(msg));
        } catch (RejectedExecutionException ignored) {
            // shutdown was called — drop the message
        }
    }
}
