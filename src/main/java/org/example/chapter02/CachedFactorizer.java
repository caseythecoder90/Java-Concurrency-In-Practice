package org.example.chapter02;

import java.math.BigInteger;
import java.util.List;

/**
 * Listing 2.8 - Servlet that Caches its Last Request and Result
 * with Properly Scoped Synchronization.
 *
 * This is the chapter's culminating example — it balances thread safety
 * with good concurrency by narrowing the scope of synchronized blocks:
 *
 *   1. A short synchronized block to CHECK the cache (fast path).
 *   2. The expensive factorization runs OUTSIDE any lock.
 *   3. A short synchronized block to UPDATE the cache.
 *
 * Also tracks a hit counter for cache effectiveness using a simple long
 * guarded by the same lock (this) as the cached state. All variables
 * participating in the class's invariants are guarded by the same lock.
 *
 * "Avoid holding locks during lengthy computations or operations at
 *  risk of not completing quickly such as network or console I/O."
 */
public class CachedFactorizer {

    private BigInteger lastNumber;
    private List<BigInteger> lastFactors;
    private long hits;
    private long cacheHits;

    private final StatelessFactorizer factorizer = new StatelessFactorizer();

    public synchronized long getHits() {
        return hits;
    }

    public synchronized double getCacheHitRatio() {
        return (hits == 0) ? 0 : (double) cacheHits / hits;
    }

    public List<BigInteger> service(BigInteger number) {
        BigInteger[] cachedNumber = new BigInteger[1];
        List<BigInteger> factors = null;

        // Short synchronized block: check cache
        synchronized (this) {
            ++hits;
            if (number.equals(lastNumber)) {
                ++cacheHits;
                factors = lastFactors;
            }
            cachedNumber[0] = lastNumber;
        }

        if (factors != null) {
            return factors;
        }

        // Expensive computation runs OUTSIDE the lock
        factors = factorizer.factor(number);

        // Short synchronized block: update cache
        synchronized (this) {
            lastNumber = number;
            lastFactors = factors;
        }

        return factors;
    }
}
