package org.example.chapter06;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Listing 6.7 / 6.8 - Web Server with Shutdown Support.
 *
 * Adds the missing piece from TaskExecutionWebServer: a way to STOP. The
 * loop catches RejectedExecutionException so a {@code stop()} call mid-loop
 * is observable; the graceful-then-forceful idiom is in {@code stop()}.
 *
 *   1. {@code shutdown()} — stop accepting, finish queued/running.
 *   2. Wait up to a deadline.
 *   3. If still running, {@code shutdownNow()} — interrupt aggressively.
 *
 * The JVM won't exit while non-daemon pool threads are alive, so
 * forgetting this leaves your process hanging at "shutdown" forever.
 */
public class LifecycleWebServer {

    private final ExecutorService exec = Executors.newFixedThreadPool(100);

    public void start(ServerSocket socket) throws IOException {
        while (!exec.isShutdown()) {
            try {
                final Socket conn = socket.accept();
                exec.execute(() -> handleRequest(conn));
            } catch (RejectedExecutionException e) {
                if (!exec.isShutdown()) {
                    log("task submission rejected", e);
                }
            }
        }
    }

    public void stop() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                exec.shutdownNow();
                if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
                    log("executor did not terminate", null);
                }
            }
        } catch (InterruptedException ie) {
            exec.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    void handleRequest(Socket connection) {
        Request req = readRequest(connection);
        if (isShutdownRequest(req)) {
            stop();
        } else {
            dispatchRequest(req);
        }
    }

    // The following helpers are stand-ins so this listing compiles; the
    // chapter focuses on the lifecycle, not the request format.

    private Request readRequest(Socket s) { return new Request(); }
    private boolean isShutdownRequest(Request r) { return false; }
    private void dispatchRequest(Request r) { /* simulated work */ }
    private void log(String msg, Throwable t) {
        System.err.println(msg + (t == null ? "" : ": " + t));
    }

    private static final class Request {}
}
