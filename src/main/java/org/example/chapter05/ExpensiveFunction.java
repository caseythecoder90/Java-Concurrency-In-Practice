package org.example.chapter05;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Listing 5.16 (part) - A Computable that simulates expensive work.
 *
 * Tests use the `invocationCount` to verify that memoization actually
 * prevents redundant calls. The real "work" is a sleep, to make races
 * easier to observe.
 */
public class ExpensiveFunction implements Computable<String, BigInteger> {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Override
    public BigInteger compute(String arg) throws InterruptedException {
        invocationCount.incrementAndGet();
        // Simulate deep thought
        Thread.sleep(25);
        return new BigInteger(arg);
    }

    public int getInvocationCount() {
        return invocationCount.get();
    }
}
