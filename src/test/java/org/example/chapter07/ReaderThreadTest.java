package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link ReaderThread} (Listing 7.11) — verifies that the
 * overridden {@code interrupt()} actually unblocks a synchronous socket
 * read by closing the socket. This is the whole point of the listing:
 * standard interruption doesn't work on socket I/O, so we have to
 * unblock it some other way.
 */
class ReaderThreadTest {

    @Test
    void interrupt_unblocksSocketRead() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch processed = new CountDownLatch(1);
            AtomicInteger bytesProcessed = new AtomicInteger();

            // Accept connection on a background thread so we can connect from the test thread.
            Thread acceptor = new Thread(() -> {
                try {
                    Socket conn = server.accept();
                    ReaderThread reader = new ReaderThread(conn) {
                        @Override
                        protected void processBuffer(byte[] buf, int count) {
                            bytesProcessed.addAndGet(count);
                            processed.countDown();
                        }
                    };
                    reader.start();
                    // Let it block in read() with no data forthcoming, then interrupt.
                    Thread.sleep(150);
                    reader.interrupt();
                    reader.join(2_000);
                    assertFalse(reader.isAlive(),
                            "ReaderThread should exit after interrupt closes its socket");
                } catch (IOException | InterruptedException e) {
                    fail(e);
                }
            });
            acceptor.start();

            try (Socket client = new Socket("localhost", server.getLocalPort())) {
                // Don't write anything — we want the reader blocked on read().
                acceptor.join(5_000);
                assertFalse(acceptor.isAlive(), "acceptor thread should have completed");
            }
        }
    }

    @Test
    void interrupt_setsStandardInterruptFlagToo() throws IOException {
        // Use a connected pair so getInputStream works without throwing.
        try (ServerSocket server = new ServerSocket(0);
             Socket client = new Socket("localhost", server.getLocalPort());
             Socket serverSide = server.accept()) {

            ReaderThread reader = new ReaderThread(serverSide);
            reader.interrupt();

            assertTrue(reader.isInterrupted(),
                    "ReaderThread.interrupt() must also set the standard interrupted flag");
            assertTrue(serverSide.isClosed(), "the underlying socket should be closed");
        }
    }
}
