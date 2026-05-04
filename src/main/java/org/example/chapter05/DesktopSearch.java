package org.example.chapter05;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Listing 5.9 - Starting the Desktop Search.
 *
 * Wires up a shared bounded BlockingQueue, one FileCrawler thread per
 * root, and N_CONSUMERS indexer threads.
 *
 * Using a bounded queue is critical: if the crawlers outpace the
 * indexers, the queue fills, crawlers block on put, and we avoid OOM.
 * This is the producer-consumer pattern's natural backpressure.
 */
public class DesktopSearch {

    private static final int BOUND = 1024;
    private static final int N_CONSUMERS = Runtime.getRuntime().availableProcessors();

    public static void startIndexing(File[] roots) {
        BlockingQueue<File> queue = new LinkedBlockingQueue<>(BOUND);
        java.io.FileFilter filter = file -> true;

        for (File root : roots) {
            new Thread(new FileCrawler(queue, filter, root)).start();
        }

        for (int i = 0; i < N_CONSUMERS; i++) {
            new Thread(new Indexer(queue)).start();
        }
    }
}
