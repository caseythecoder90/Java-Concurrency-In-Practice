package org.example.chapter07;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Listing 7.1 - Using a Volatile Field to Hold Cancellation State.
 *
 * The simplest cooperative cancellation pattern: a {@code volatile boolean}
 * flag polled in the task loop. {@code cancel()} sets the flag; the loop
 * notices on its next iteration and exits.
 *
 * Works fine for tight CPU-bound loops like this one. The flag MUST be
 * volatile — otherwise the writer's update may never become visible to
 * the runner, and the loop would never see the cancellation request.
 *
 * The pattern breaks the moment the task makes a blocking call that
 * doesn't check the flag. See {@link BrokenPrimeProducer} for a worked
 * example of that failure, and {@link PrimeProducer} for the fix.
 */
public class PrimeGenerator implements Runnable {

    private final List<BigInteger> primes = new ArrayList<>();
    private volatile boolean cancelled;

    @Override
    public void run() {
        BigInteger p = BigInteger.ONE;
        while (!cancelled) {
            p = p.nextProbablePrime();
            synchronized (this) {
                primes.add(p);
            }
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public synchronized List<BigInteger> get() {
        return new ArrayList<>(primes);
    }
}
