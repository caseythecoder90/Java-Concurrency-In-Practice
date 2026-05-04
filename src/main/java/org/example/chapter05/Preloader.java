package org.example.chapter05;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Listing 5.12 - Using FutureTask to Preload Data that is Needed Later.
 *
 * Kicks off an expensive load (here simulated) on a background thread
 * so that when the program later calls get(), the result is (usually)
 * already available.
 *
 * Note: the thread is started in start(), NOT in the constructor. This
 * is the Chapter 3 rule — don't start threads from a constructor,
 * because it can let the `this` reference escape before construction
 * finishes. A dedicated start() method is the safe pattern.
 *
 * Future.get() provides SAFE PUBLICATION of the result from the
 * computing thread to the retrieving thread.
 */
public class Preloader {

    public record ProductInfo(String payload) {
    }

    public static final class DataLoadException extends Exception {
        public DataLoadException(String msg) { super(msg); }
    }

    private final FutureTask<ProductInfo> future = new FutureTask<>(new Callable<ProductInfo>() {
        @Override
        public ProductInfo call() throws DataLoadException {
            return loadProductInfo();
        }
    });
    private final Thread thread = new Thread(future);

    public void start() {
        thread.start();
    }

    public ProductInfo get() throws DataLoadException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DataLoadException dle) {
                // Known checked exception — rethrow directly
                throw dle;
            }
            // Everything else is unchecked; launderThrowable figures out what to do
            throw LaunderThrowable.launderThrowable(cause);
        }
    }

    private ProductInfo loadProductInfo() throws DataLoadException {
        // Simulate a slow load
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataLoadException("interrupted while loading");
        }
        return new ProductInfo("preloaded");
    }
}
