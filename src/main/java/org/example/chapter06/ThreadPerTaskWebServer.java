package org.example.chapter06;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listing 6.2 - Web Server That Starts a New Thread for Each Request.
 *
 * Better than the sequential server in three ways:
 *   1. The accept loop is no longer blocked behind request handling.
 *   2. Multiple requests run concurrently — useful when each is mostly I/O.
 *   3. Tasks run in parallel on multi-core machines.
 *
 * But it has serious problems:
 *   - Thread creation/teardown is not free (memory, latency).
 *   - Resource consumption is unbounded — a burst can spawn thousands of
 *     threads.
 *   - Stability is at risk — exceeding the JVM/OS thread limit throws
 *     OutOfMemoryError ("unable to create native thread").
 *   - Throughput plateaus then degrades as thread count grows past cores.
 *
 * This is why we need a bounded Executor (Listing 6.3 onward).
 */
public class ThreadPerTaskWebServer {

    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            new Thread(() -> WebServerHandler.handleRequest(connection)).start();
        }
    }
}
