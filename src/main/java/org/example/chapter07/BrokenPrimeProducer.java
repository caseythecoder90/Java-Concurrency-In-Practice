package org.example.chapter07;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;

/**
 * Listing 7.3 - Unreliable Cancellation that can Leave Producers Stuck in
 * a Blocking Operation. Don't Do This.
 *
 * The flag-based cancellation pattern from {@link PrimeGenerator} stops
 * working the moment the loop calls a blocking method. {@code queue.put}
 * blocks when the queue is full; if the consumer stops draining, the
 * producer wedges forever. {@code cancel()} dutifully sets the flag —
 * but the producer never re-checks it because it's stuck in {@code put}.
 *
 * This class is included to demonstrate the bug. Real code should use
 * {@link PrimeProducer}, which switches to interruption.
 */
public class BrokenPrimeProducer extends Thread {

    private final BlockingQueue<BigInteger> queue;
    private volatile boolean cancelled = false;

    public BrokenPrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            while (!cancelled) {
                queue.put(p = p.nextProbablePrime());
            }
        } catch (InterruptedException consumed) {
            // swallowed — see PrimeProducer for the right approach
        }
    }

    public void cancel() {
        cancelled = true;
    }
}
