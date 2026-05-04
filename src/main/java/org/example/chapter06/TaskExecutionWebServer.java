package org.example.chapter06;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Listing 6.3 - Web Server Using a Thread Pool.
 *
 * Same loop shape as ThreadPerTaskWebServer, but submission is decoupled
 * from execution: the Executor decides how, where, and when to run each
 * request. We get bounded threads, a built-in queue under burst load,
 * and the ability to swap policies (fixed vs cached vs custom) by
 * touching exactly one line.
 *
 * This is the canonical structure for a server in production-ish shape.
 * Lifecycle is still missing — see LifecycleWebServer for shutdown.
 */
public class TaskExecutionWebServer {

    private static final int NTHREADS = 100;
    private static final Executor exec = Executors.newFixedThreadPool(NTHREADS);

    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            final Socket connection = socket.accept();
            exec.execute(() -> WebServerHandler.handleRequest(connection));
        }
    }
}
