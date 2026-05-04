package org.example.chapter06;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;

import static org.example.chapter06.LaunderThrowable.launderThrowable;

/**
 * Listing 6.15 - Using CompletionService to Render Page Elements as
 * They Become Available.
 *
 * The page-renderer endpoint of the chapter. Each image download is
 * submitted independently; CompletionService returns Futures in the
 * order they complete, so we render each image the moment its download
 * finishes — not after the slowest.
 *
 *   submit all → render text → for N times: take(), render the result.
 *
 * The execution-order test in {@link
 * org.example.chapter06.RendererTest} verifies this: a slow image
 * submitted first comes out AFTER a fast image submitted later.
 */
public class CompletionRenderer {

    private final Executor executor;

    public CompletionRenderer(Executor executor) {
        this.executor = executor;
    }

    public List<String> renderPage(String text, List<ImageInfo> infos)
            throws InterruptedException {

        CompletionService<ImageData> cs = new ExecutorCompletionService<>(executor);
        for (ImageInfo info : infos) {
            cs.submit(info::downloadImage);
        }

        List<String> events = new ArrayList<>();
        events.add("text:" + text);

        try {
            for (int i = 0; i < infos.size(); i++) {
                Future<ImageData> f = cs.take();
                events.add("img:" + f.get().label());
            }
        } catch (ExecutionException e) {
            throw launderThrowable(e.getCause());
        }
        return events;
    }
}
