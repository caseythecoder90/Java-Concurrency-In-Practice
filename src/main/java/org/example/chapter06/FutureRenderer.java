package org.example.chapter06;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.example.chapter06.LaunderThrowable.launderThrowable;

/**
 * Listing 6.12 - Waiting for Image Download with Future.
 *
 * Submits the entire image-download phase as a single Callable, then
 * renders text on the calling thread while downloads run in the
 * background. Better than {@link SequentialRenderer} — text appears
 * immediately — but image rendering still happens as a single batch
 * after the slowest image finishes.
 *
 * Note the launderThrowable / interrupt handling around get(): the
 * callable can throw an InterruptedException (caught and re-flagged) or
 * any unchecked Throwable (unwrapped from ExecutionException).
 */
public class FutureRenderer {

    private final ExecutorService executor;

    public FutureRenderer(ExecutorService executor) {
        this.executor = executor;
    }

    public List<String> renderPage(String text, final List<ImageInfo> infos)
            throws InterruptedException {

        Callable<List<ImageData>> downloadAll = () -> {
            List<ImageData> result = new ArrayList<>();
            for (ImageInfo info : infos) {
                result.add(info.downloadImage());   // serial inside the task
            }
            return result;
        };
        Future<List<ImageData>> future = executor.submit(downloadAll);

        List<String> events = new ArrayList<>();
        events.add("text:" + text);                  // overlaps download

        try {
            for (ImageData img : future.get()) {
                events.add("img:" + img.label());
            }
        } catch (ExecutionException e) {
            future.cancel(true);
            throw launderThrowable(e.getCause());
        }
        return Collections.unmodifiableList(events);
    }
}
