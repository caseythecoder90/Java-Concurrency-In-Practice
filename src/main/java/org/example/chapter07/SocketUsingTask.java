package org.example.chapter07;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

/**
 * Listing 7.12 - A {@link CancellableTask} that closes a socket on cancel.
 *
 * Combines the two cancellation mechanisms:
 *   - The custom Future's {@code cancel()} closes the socket (escapes
 *     blocking read/write that aren't interruptible).
 *   - {@code super.cancel(mayInterruptIfRunning)} also interrupts the
 *     worker thread for any non-socket interruptible blocking.
 *
 * The subclass implements {@code call()} to do the actual work; this
 * abstract base just gives it a managed socket reference and the
 * cancellation hook.
 *
 * @param <T> task result type
 */
public abstract class SocketUsingTask<T> implements CancellableTask<T> {

    private Socket socket;

    protected synchronized void setSocket(Socket s) {
        this.socket = s;
    }

    @Override
    public synchronized void cancel() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public RunnableFuture<T> newTask() {
        return new FutureTask<T>(this) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                try {
                    SocketUsingTask.this.cancel();
                } finally {
                    return super.cancel(mayInterruptIfRunning);
                }
            }
        };
    }
}
