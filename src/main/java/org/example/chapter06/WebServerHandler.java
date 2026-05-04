package org.example.chapter06;

import java.io.IOException;
import java.net.Socket;

/**
 * Shared placeholder for the {@code handleRequest(Socket)} method that
 * every web-server variant in chapter 6 calls. The book's listings reference
 * a {@code handleRequest} without specifying it; this gives the listings
 * something concrete to compile against.
 *
 * The implementation is deliberately trivial — sleep briefly, close the
 * socket. The point of the chapter isn't request handling; it's how each
 * server submits the work.
 */
final class WebServerHandler {

    private WebServerHandler() {}

    static void handleRequest(Socket connection) {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try { connection.close(); } catch (IOException ignored) {}
        }
    }
}
