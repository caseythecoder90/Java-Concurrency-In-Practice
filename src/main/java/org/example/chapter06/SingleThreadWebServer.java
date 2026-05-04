package org.example.chapter06;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listing 6.1 - Sequential Web Server.
 *
 * Correct, but disastrous in production: while one request is being
 * handled, accept() is not called — additional clients sit in the OS
 * backlog or are refused. Throughput is roughly 1 / mean(handleRequest)
 * per second, on a single core.
 *
 * The fix in 6.2 is thread-per-task; the ultimate fix in 6.3 is to use
 * an Executor.
 */
public class SingleThreadWebServer {

    public static void main(String[] args) throws IOException {
        ServerSocket socket = new ServerSocket(80);
        while (true) {
            Socket connection = socket.accept();
            WebServerHandler.handleRequest(connection);
        }
    }
}