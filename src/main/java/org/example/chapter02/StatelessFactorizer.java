package org.example.chapter02;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Listing 2.1 - A Stateless Servlet.
 *
 * This class has no fields — all state lives in local variables on the
 * thread's stack. Local variables are not shared between threads, so
 * no two threads can interfere with each other.
 *
 * Stateless objects are always thread-safe.
 */
public class StatelessFactorizer {

    /**
     * Computes the prime factors of a number.
     * All variables (i, n, factors) are local to the calling thread.
     */
    public List<BigInteger> factor(BigInteger number) {
        List<BigInteger> factors = new ArrayList<>();
        BigInteger n = number;
        BigInteger divisor = BigInteger.TWO;

        while (divisor.multiply(divisor).compareTo(n) <= 0) {
            while (n.mod(divisor).equals(BigInteger.ZERO)) {
                factors.add(divisor);
                n = n.divide(divisor);
            }
            divisor = divisor.add(BigInteger.ONE);
        }
        if (n.compareTo(BigInteger.ONE) > 0) {
            factors.add(n);
        }
        return factors;
    }
}
