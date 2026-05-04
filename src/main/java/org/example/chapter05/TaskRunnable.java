package org.example.chapter05;

import java.util.concurrent.BlockingQueue;

/**
 * Listing 5.10 - Restoring the Interrupted Status so as Not to Swallow the Interrupt.
 *
 * Runnable.run() cannot throw checked exceptions, so we can't propagate
 * InterruptedException. But swallowing it destroys evidence that the
 * thread was interrupted — callers up the stack lose the chance to
 * respond to cancellation.
 *
 * The correct move when you can't propagate: RESTORE the interrupted
 * status by calling Thread.currentThread().interrupt(). Higher-level
 * code that checks `isInterrupted()` can then see the request and act.
 *
 * NEVER catch InterruptedException and do nothing. The only exception
 * is when you yourself are extending Thread and therefore own all the
 * code up the stack.
 */
public class TaskRunnable implements Runnable {

    private final BlockingQueue<Runnable> queue;

    public TaskRunnable(BlockingQueue<Runnable> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            Runnable task = queue.take();
            task.run();
        } catch (InterruptedException e) {
            // restore interrupted status so callers can see it
            Thread.currentThread().interrupt();
        }
    }
}
