package org.example.chapter06;

import java.util.concurrent.Callable;

/**
 * Callable that simulates soliciting a travel quote from a company:
 * sleeps for {@code latencyMillis} (network round-trip), then returns a
 * quote — unless interrupted, in which case it propagates the
 * InterruptedException so {@code Future.cancel(true)} actually stops it.
 *
 * Used by {@link TravelPortal} and the timed-invokeAll demonstration.
 */
public class TravelQuoteTask implements Callable<TravelQuote> {

    private final String company;
    private final long latencyMillis;
    private final double price;

    public TravelQuoteTask(String company, long latencyMillis, double price) {
        this.company = company;
        this.latencyMillis = latencyMillis;
        this.price = price;
    }

    @Override
    public TravelQuote call() throws InterruptedException {
        Thread.sleep(latencyMillis);
        return new TravelQuote(company, price, "ok");
    }

    public String company() { return company; }
}
