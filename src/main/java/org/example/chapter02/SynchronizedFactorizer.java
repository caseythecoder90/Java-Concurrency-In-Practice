package org.example.chapter02;

import java.math.BigInteger;
import java.util.List;

/**
 * Listing 2.6 - Servlet that Caches Last Result, but with
 * Unacceptably Poor Concurrency.
 *
 * Synchronizing the entire service method is the simplest way to make
 * this class thread-safe — but it serializes ALL requests. Only one
 * thread can execute service() at a time, turning this into a
 * single-threaded servlet and creating a severe performance bottleneck.
 *
 * This is safe but not concurrent. The expensive factorization runs
 * while holding the lock, blocking every other thread.
 */
public class SynchronizedFactorizer {

    private BigInteger lastNumber;
    private List<BigInteger> lastFactors;

    private final StatelessFactorizer factorizer = new StatelessFactorizer();

    public synchronized List<BigInteger> service(BigInteger number) {
        if (number.equals(lastNumber)) {
            return lastFactors;
        }

        // The factorization runs inside the lock — every other thread
        // is blocked until this completes.
        List<BigInteger> factors = factorizer.factor(number);
        lastNumber = number;
        lastFactors = factors;
        return factors;
    }
}
