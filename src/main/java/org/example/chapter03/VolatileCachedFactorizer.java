package org.example.chapter03;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Listing 3.13 - Caching the Last Result Using a Volatile Reference to
 * an Immutable Holder Object.
 *
 * The two-field caching problem from chapter 2 — where using two
 * AtomicReferences for number and factors was unsafe — is solved by
 * combining:
 *   1. An IMMUTABLE HOLDER ({@link OneValueCache}) that bundles the
 *      related state into a single object. Any thread that sees the
 *      reference sees a consistent snapshot.
 *   2. A VOLATILE REFERENCE to the holder. Assigning a new holder is
 *      instantly visible to other threads.
 *
 * Result: thread-safe caching without any explicit locking.
 */
public class VolatileCachedFactorizer {

    private volatile OneValueCache cache = new OneValueCache(null, null);

    public List<BigInteger> service(BigInteger number) {
        BigInteger[] factors = cache.getFactors(number);
        if (factors == null) {
            factors = factor(number);
            cache = new OneValueCache(number, factors);
        }
        return new ArrayList<>(List.of(factors));
    }

    private BigInteger[] factor(BigInteger n) {
        List<BigInteger> factors = new ArrayList<>();
        BigInteger num = n;
        BigInteger divisor = BigInteger.TWO;
        while (divisor.multiply(divisor).compareTo(num) <= 0) {
            while (num.mod(divisor).equals(BigInteger.ZERO)) {
                factors.add(divisor);
                num = num.divide(divisor);
            }
            divisor = divisor.add(BigInteger.ONE);
        }
        if (num.compareTo(BigInteger.ONE) > 0) {
            factors.add(num);
        }
        return factors.toArray(new BigInteger[0]);
    }
}
