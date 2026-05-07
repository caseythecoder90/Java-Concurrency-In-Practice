package org.example.chapter07;

import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;

/**
 * Listing 7.12 - Encapsulating Nonstandard Cancellation in a Task with
 * newTaskFor.
 *
 * The task-side counterpart of {@link ReaderThread}: instead of overriding
 * {@code Thread.interrupt}, override {@code Future.cancel} so cancellation
 * does extra work (close a socket, log, gather metrics, etc).
 *
 * A {@code CancellableTask} contributes its own RunnableFuture via
 * {@link #newTask()}. {@link CancellingExecutor} hooks into
 * {@code newTaskFor} to use it.
 *
 * @param <T> task result type
 */
public interface CancellableTask<T> extends Callable<T> {

    /** Custom cancellation logic — invoked by the custom Future. */
    void cancel();

    /** Provide a Future whose cancel() invokes {@link #cancel()}. */
    RunnableFuture<T> newTask();
}
