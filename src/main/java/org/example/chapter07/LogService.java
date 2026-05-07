package org.example.chapter07;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Listing 7.15 - Adding Reliable Cancellation to a Producer-Consumer
 * Logging Service.
 *
 * The naïve "set a shutdown flag and check it in log()" approach
 * (Listing 7.14) is a check-then-act race: a producer can read
 * isShutdown==false, get pre-empted, and the consumer can shut down
 * before the producer reaches put() — leaving the producer wedged on a
 * queue no one will ever drain.
 *
 * The fix here:
 *   - Hold the lock across the (isShutdown? + reservation++) check.
 *   - Release the lock BEFORE the blocking put — never hold a lock while
 *     blocking.
 *   - Consumer keeps draining until isShutdown && reservations == 0.
 *
 * The pattern is general: lock the decision, not the I/O.
 */
public class LogService {

    private final BlockingQueue<String> queue;
    private final LoggerThread loggerThread;
    private final PrintWriter writer;

    private boolean isShutdown;
    private int reservations;

    public LogService(Writer writer, int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.writer = new PrintWriter(writer, true);
        this.loggerThread = new LoggerThread();
    }

    public void start() {
        loggerThread.start();
    }

    public void stop() {
        synchronized (this) {
            isShutdown = true;
        }
        loggerThread.interrupt();
    }

    public void awaitTermination() throws InterruptedException {
        loggerThread.join();
    }

    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            if (isShutdown) {
                throw new IllegalStateException("logger is shut down");
            }
            ++reservations;
        }
        queue.put(msg);
    }

    private class LoggerThread extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        synchronized (LogService.this) {
                            if (isShutdown && reservations == 0) {
                                break;
                            }
                        }
                        String msg = queue.take();
                        synchronized (LogService.this) {
                            --reservations;
                        }
                        writer.println(msg);
                    } catch (InterruptedException e) {
                        /* retry — we want to drain reservations */
                    }
                }
            } finally {
                writer.close();
            }
        }
    }
}
