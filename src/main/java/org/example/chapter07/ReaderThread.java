package org.example.chapter07;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

/**
 * Listing 7.11 - Encapsulating Nonstandard Cancellation in a Thread by
 * Overriding Interrupt.
 *
 * Synchronous socket I/O ({@code InputStream.read}, {@code OutputStream.write})
 * does NOT respond to interruption. Setting the thread's interrupt flag
 * has no effect — the call stays blocked until the socket has bytes,
 * is closed, or fails.
 *
 * The escape hatch: closing the socket from outside makes the blocked
 * read throw SocketException. ReaderThread combines both — overriding
 * {@code interrupt} so a cancellation request closes the socket AND sets
 * the standard interrupt flag.
 *
 * Result: callers cancel ReaderThread the same way they'd cancel any
 * other thread (call interrupt), and it works whether the thread is
 * blocked in read OR in some other interruptible operation.
 */
public class ReaderThread extends Thread {

    private static final int BUFSZ = 4096;

    private final Socket socket;
    private final InputStream in;

    public ReaderThread(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
    }

    @Override
    public void interrupt() {
        try {
            socket.close();
        } catch (IOException ignored) {
        } finally {
            super.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            byte[] buf = new byte[BUFSZ];
            while (true) {
                int count = in.read(buf);
                if (count < 0) {
                    break;
                } else if (count > 0) {
                    processBuffer(buf, count);
                }
            }
        } catch (IOException e) {
            /* allow thread to exit */
        }
    }

    /**
     * Override or wire up to do the actual byte handling. Default
     * implementation is a no-op so this class is testable without
     * pulling in protocol details.
     */
    protected void processBuffer(byte[] buf, int count) {
        // no-op
    }
}
