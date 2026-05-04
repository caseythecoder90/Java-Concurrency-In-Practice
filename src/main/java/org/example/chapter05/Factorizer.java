package org.example.chapter05;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Listing 5.20 - Factorizing Servlet that Caches Results Using Memoizer.
 *
 * The payoff: the factoring servlet from Chapter 2, now with a real,
 * scalable, correct cache courtesy of Memoizer.
 *
 * The servlet glue is stubbed out with a simple `service` method
 * taking a BigInteger directly — the original used ServletRequest.
 *
 * Memoizer guarantees:
 *   - Each distinct input is factored at most once.
 *   - Concurrent calls for the same input share one computation.
 *   - Concurrent calls for different inputs proceed in parallel.
 */
public class Factorizer {

    private final Computable<BigInteger, BigInteger[]> cache =
            new Memoizer<>(this::factor);

    public BigInteger[] service(BigInteger number) throws InterruptedException {
        return cache.compute(number);
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
