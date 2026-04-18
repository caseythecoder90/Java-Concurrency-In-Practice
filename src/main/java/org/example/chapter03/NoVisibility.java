package org.example.chapter03;

/**
 * Listing 3.1 - Sharing Variables without Synchronization. Don't Do This.
 *
 * Two threads share ready and number with no synchronization.
 * Three outcomes are possible:
 *   1. Prints 42 (the happy path).
 *   2. Prints 0 — the write to `ready` becomes visible before the
 *      write to `number` due to reordering.
 *   3. Loops forever — the write to `ready` never becomes visible
 *      to the reader thread.
 *
 * Without synchronization, there is no guarantee that writes from one
 * thread will ever be seen by another, and reorderings between writes
 * are permitted as long as they aren't detectable in the writing thread.
 */
public class NoVisibility {

    private static boolean ready;
    private static int number;

    private static class ReaderThread extends Thread {
        @Override
        public void run() {
            while (!ready) {
                Thread.yield();
            }
            System.out.println(number);
        }
    }

    public static void main(String[] args) {
        new ReaderThread().start();
        number = 42;
        ready = true;
    }
}
