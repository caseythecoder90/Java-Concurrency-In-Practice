package org.example.chapter03;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Listing 3.12 - Immutable Holder for Caching a Number and its Factors.
 *
 * OneValueCache is immutable:
 *   - lastNumber and lastFactors are final.
 *   - Defensive array copies in constructor and getter prevent callers
 *     from mutating the internal array.
 *
 * Because it's immutable, any thread that sees a OneValueCache
 * reference is guaranteed to see a consistent snapshot of the cached
 * number and factors. Combined with a volatile reference in
 * {@link VolatileCachedFactorizer}, this gives thread safety without
 * any explicit locking.
 */
public final class OneValueCache {

    private final BigInteger lastNumber;
    private final BigInteger[] lastFactors;

    public OneValueCache(BigInteger i, BigInteger[] factors) {
        this.lastNumber = i;
        this.lastFactors = (factors == null) ? null : Arrays.copyOf(factors, factors.length);
    }

    public BigInteger[] getFactors(BigInteger i) {
        if (lastNumber == null || !lastNumber.equals(i)) {
            return null;
        }
        return Arrays.copyOf(lastFactors, lastFactors.length);
    }
}
