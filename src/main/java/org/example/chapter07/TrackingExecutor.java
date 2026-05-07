package org.example.chapter07;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Listing 7.21 - ExecutorService that Keeps Track of Cancelled Tasks
 * After Shutdown.
 *
 * {@code shutdownNow} returns the list of tasks that were submitted but
 * never STARTED. It says nothing about tasks that started but didn't
 * FINISH. TrackingExecutor closes that gap by wrapping each submitted
 * Runnable: when the wrapper's run returns, if the executor is shut down
 * AND the worker thread's interrupt flag is set, record the original
 * Runnable as cancelled-in-progress.
 *
 * After {@code shutdownNow + awaitTermination}, callers can ask for both:
 *   - shutdownNow's return value: never-started tasks
 *   - {@link #getCancelledTasks()}: started-but-cancelled tasks
 *
 * Combine the two and you have the full picture of "what didn't finish."
 *
 * Race condition (acknowledged): a task that completes its last instruction
 * just before the worker is interrupted may be misclassified as cancelled.
 * Tolerable when tasks are idempotent (safe to re-run).
 */
public class TrackingExecutor extends AbstractExecutorService {

    private final ExecutorService exec;
    private final Set<Runnable> tasksCancelledAtShutdown =
            Collections.synchronizedSet(new HashSet<>());

    public TrackingExecutor(ExecutorService exec) {
        this.exec = exec;
    }

    public List<Runnable> getCancelledTasks() {
        if (!exec.isTerminated()) {
            throw new IllegalStateException("executor has not terminated");
        }
        return new ArrayList<>(tasksCancelledAtShutdown);
    }

    @Override
    public void execute(final Runnable runnable) {
        exec.execute(() -> {
            try {
                runnable.run();
            } finally {
                if (isShutdown() && Thread.currentThread().isInterrupted()) {
                    tasksCancelledAtShutdown.add(runnable);
                }
            }
        });
    }

    @Override
    public void shutdown() { exec.shutdown(); }

    @Override
    public List<Runnable> shutdownNow() { return exec.shutdownNow(); }

    @Override
    public boolean isShutdown() { return exec.isShutdown(); }

    @Override
    public boolean isTerminated() { return exec.isTerminated(); }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }
}
