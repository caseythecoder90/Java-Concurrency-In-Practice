package org.example.chapter02;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listing 2.5 - Servlet that Attempts to Cache its Last Result
 * without Adequate Atomicity.
 *
 * Even though lastNumber and lastFactors are each individually atomic
 * (AtomicReference), the two updates are NOT atomic with respect to
 * each other. A thread could see the new lastNumber with the old
 * lastFactors, returning incorrect cached results.
 *
 * This violates the invariant: lastFactors must always be the factors
 * of lastNumber. Two atomic variables ≠ one atomic operation.
 *
 * "To preserve state consistency, update related state variables
 *  in a single atomic operation."
 */
public class UnsafeCachingFactorizer {

    private final AtomicReference<BigInteger> lastNumber = new AtomicReference<>();
    private final AtomicReference<List<BigInteger>> lastFactors = new AtomicReference<>();

    private final StatelessFactorizer factorizer = new StatelessFactorizer();

    public List<BigInteger> service(BigInteger number) {
        if (number.equals(lastNumber.get())) {
            return lastFactors.get(); // may return stale factors!
        }

        List<BigInteger> factors = factorizer.factor(number);

        // These two updates are NOT atomic together — a thread can
        // read between them and see an inconsistent pair.
        lastNumber.set(number);
        lastFactors.set(factors);

        return factors;
    }
}
