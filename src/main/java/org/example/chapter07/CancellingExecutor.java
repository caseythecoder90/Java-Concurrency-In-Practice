package org.example.chapter07;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Listing 7.12 - ThreadPoolExecutor that lets {@link CancellableTask}s
 * supply their own RunnableFuture.
 *
 * Plain {@code ThreadPoolExecutor} wraps every submitted Callable in a
 * default FutureTask. Override {@code newTaskFor} to detect tasks that
 * carry their own cancellation logic and let them produce their own
 * Future.
 *
 * This is the cleanest extension point for "I want cancel() to do more
 * than just interrupt the worker thread."
 */
public class CancellingExecutor extends ThreadPoolExecutor {

    public CancellingExecutor(int corePoolSize, int maximumPoolSize,
                              long keepAliveTime, TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableTask) {
            return ((CancellableTask<T>) callable).newTask();
        }
        return super.newTaskFor(callable);
    }
}
