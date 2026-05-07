package org.example.chapter07;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Listings 7.17, 7.18, 7.19 - Shutdown with a Poison Pill.
 *
 * A single-producer / single-consumer pipeline: the {@code CrawlerThread}
 * walks the file system and {@code put}s files on the queue; the
 * {@code IndexerThread} {@code take}s them off and indexes them.
 *
 * Shutdown protocol:
 *   - The application calls {@link #stop()} which interrupts the producer.
 *   - The producer's finally block puts a sentinel POISON file on the queue.
 *   - The consumer keeps draining; when it pulls POISON, it exits.
 *
 * Why this pattern works:
 *   - FIFO queue — anything submitted before POISON gets processed first.
 *   - Unbounded queue — producer can always enqueue POISON without blocking.
 *   - Known counts — exactly one producer, one consumer.
 *
 * Adapted from the book's File-system crawler so it's testable without
 * actually walking a directory tree: the constructor takes a list of
 * files, the producer enqueues them, and we expose the indexed list.
 */
public class IndexingService {

    /** Sentinel meaning "stop after this." */
    static final String POISON = "__POISON__";

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final List<String> indexed = new ArrayList<>();
    private final List<String> source;
    private final CrawlerThread producer = new CrawlerThread();
    private final IndexerThread consumer = new IndexerThread();

    public IndexingService(List<String> source) {
        this.source = List.copyOf(source);
    }

    public void start() {
        producer.start();
        consumer.start();
    }

    public void stop() {
        producer.interrupt();
    }

    public void awaitTermination() throws InterruptedException {
        consumer.join();
    }

    public synchronized List<String> indexed() {
        return new ArrayList<>(indexed);
    }

    private class CrawlerThread extends Thread {
        @Override
        public void run() {
            try {
                for (String f : source) {
                    queue.put(f);
                }
            } catch (InterruptedException e) {
                /* fall through to enqueue POISON */
            } finally {
                while (true) {
                    try {
                        queue.put(POISON);
                        break;
                    } catch (InterruptedException retry) {
                        /* the put MUST happen — keep trying */
                    }
                }
            }
        }
    }

    private class IndexerThread extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    String file = queue.take();
                    if (file.equals(POISON)) {
                        break;
                    }
                    indexFile(file);
                }
            } catch (InterruptedException consumed) {
                /* exit */
            }
        }
    }

    private synchronized void indexFile(String file) {
        indexed.add(file);
    }
}
