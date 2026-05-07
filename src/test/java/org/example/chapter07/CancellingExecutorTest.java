package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the newTaskFor / CancellableTask machinery (Listing 7.12).
 *
 * The key behavior: when a {@link SocketUsingTask} is submitted to a
 * {@link CancellingExecutor} and then cancelled via its Future, the
 * custom Future closes the socket (escaping the non-interruptible read)
 * AND interrupts the worker thread.
 */
class CancellingExecutorTest {

    @Test
    void cancellingFuture_closesSocketAndUnblocksRead() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            AtomicBoolean closedDuringRead = new AtomicBoolean();

            SocketUsingTask<String> task = new SocketUsingTask<>() {
                @Override
                public String call() throws Exception {
                    try (Socket s = new Socket("localhost", server.getLocalPort())) {
                        setSocket(s);
                        try (InputStream in = s.getInputStream()) {
                            byte[] buf = new byte[1024];
                            // Will block until cancel() closes the socket.
                            int n = in.read(buf);
                            // After socket close, read returns -1 (or throws SocketException).
                            closedDuringRead.set(true);
                            return "read returned " + n;
                        }
                    } catch (IOException e) {
                        closedDuringRead.set(true);
                        return "socket exception: " + e.getMessage();
                    }
                }
            };

            CancellingExecutor exec = new CancellingExecutor(
                    1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                Future<String> future = exec.submit(task);
                // Accept the connection so the task can proceed to read.
                try (Socket accepted = server.accept()) {
                    Thread.sleep(150);                  // make sure read() is blocked
                    assertTrue(future.cancel(true));
                    Thread.sleep(150);                  // give the task time to unwind
                }
                assertTrue(future.isCancelled());
                assertTrue(closedDuringRead.get(),
                        "the read() inside call() should have unblocked when the socket was closed");
            } finally {
                exec.shutdownNow();
                assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
            }
        }
    }
}
