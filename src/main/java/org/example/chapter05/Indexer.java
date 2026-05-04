package org.example.chapter05;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listing 5.8 (consumer half) - Consumer Task in a Desktop Search Application.
 *
 * Repeatedly takes files from the queue and "indexes" them. Uses
 * BlockingQueue.take(), which blocks when the queue is empty, so the
 * consumer doesn't need to busy-poll.
 *
 * As in the book, this consumer runs forever until interrupted —
 * Chapter 7 covers graceful shutdown techniques.
 *
 * `indexedCount` is exposed for tests so they can observe progress.
 */
public class Indexer implements Runnable {

    private final BlockingQueue<File> queue;
    private final AtomicInteger indexedCount = new AtomicInteger();

    public Indexer(BlockingQueue<File> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            while (true) {
                indexFile(queue.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void indexFile(File f) {
        // Placeholder for the real indexing work
        indexedCount.incrementAndGet();
    }

    public int getIndexedCount() {
        return indexedCount.get();
    }
}
