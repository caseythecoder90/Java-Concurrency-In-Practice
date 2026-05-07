package org.example.chapter07;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;

/**
 * Listing 7.5 - Using Interruption for Cancellation.
 *
 * The fixed version of {@link BrokenPrimeProducer}. Two cancellation
 * points per iteration:
 *
 *   1. {@code Thread.currentThread().isInterrupted()} at the top of the loop —
 *      checks the interrupt flag before doing the lengthy work of finding
 *      the next prime.
 *   2. The blocking {@code queue.put} call — interruptible, so it throws
 *      InterruptedException when the producer thread is interrupted.
 *
 * Either way, calling {@link #cancel()} (which calls {@code interrupt()})
 * causes the producer to exit promptly.
 *
 * The explicit poll isn't strictly necessary — {@code put} would handle it —
 * but it makes the producer responsive to interruption BEFORE starting the
 * next prime search. When interruptible blocking calls are infrequent in
 * the loop, an explicit poll improves responsiveness.
 *
 * Note that we swallow {@code InterruptedException} in {@code run()} on
 * purpose: this thread IS the cancellation target, and it's about to
 * terminate. There is no code higher up the stack that needs the signal.
 * In general-purpose task code you'd restore the flag with
 * {@code Thread.currentThread().interrupt()} instead.
 */
public class PrimeProducer extends Thread {

    private final BlockingQueue<BigInteger> queue;

    public PrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            while (!Thread.currentThread().isInterrupted()) {
                queue.put(p = p.nextProbablePrime());
            }
        } catch (InterruptedException consumed) {
            /* allow thread to exit */
        }
    }

    public void cancel() {
        interrupt();
    }
}
