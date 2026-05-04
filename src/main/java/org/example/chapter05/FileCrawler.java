package org.example.chapter05;

import java.io.File;
import java.io.FileFilter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Listing 5.8 (producer half) - Producer Task in a Desktop Search Application.
 *
 * Recursively walks a file hierarchy and puts files matching `fileFilter`
 * onto the shared `fileQueue`. Blocks on `put` if the queue is full
 * (bounded-queue backpressure).
 *
 * A simple already-seen set prevents re-queuing the same file on reruns.
 */
public class FileCrawler implements Runnable {

    private final BlockingQueue<File> fileQueue;
    private final FileFilter fileFilter;
    private final File root;
    private final Set<File> indexed = new HashSet<>();

    public FileCrawler(BlockingQueue<File> fileQueue, FileFilter fileFilter, File root) {
        this.fileQueue = fileQueue;
        this.fileFilter = fileFilter;
        this.root = root;
    }

    @Override
    public void run() {
        try {
            crawl(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void crawl(File dir) throws InterruptedException {
        File[] entries = dir.listFiles(fileFilter);
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                crawl(entry);
            } else if (!alreadyIndexed(entry)) {
                fileQueue.put(entry);
            }
        }
    }

    private boolean alreadyIndexed(File f) {
        return !indexed.add(f);
    }
}
