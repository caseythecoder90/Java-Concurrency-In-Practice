package org.example.chapter06;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TravelPortal} — the timed-invokeAll pattern (Listing 6.17).
 *
 * Verifies that:
 *   - Tasks finishing before the deadline produce real quotes.
 *   - Tasks not finishing produce a "timeout" sentinel quote, not an
 *     exception that aborts the whole call.
 *   - The total wall-clock time is bounded by the deadline (within
 *     reasonable test slop).
 */
class TravelPortalTest {

    private ExecutorService exec;

    @BeforeEach
    void setUp() {
        exec = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        exec.shutdownNow();
        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
    }

    @Test
    void allWithinDeadline_returnQuotesSortedByPrice() throws InterruptedException {
        TravelPortal portal = new TravelPortal(exec);
        List<TravelQuoteTask> tasks = List.of(
                new TravelQuoteTask("delta",   30, 250.00),
                new TravelQuoteTask("united",  30, 199.99),
                new TravelQuoteTask("jetblue", 30, 220.00));

        List<TravelQuote> quotes = portal.getRankedQuotes(tasks, 1, TimeUnit.SECONDS);

        assertEquals(3, quotes.size());
        assertTrue(quotes.stream().allMatch(q -> q.status().equals("ok")));
        assertEquals("united", quotes.get(0).company());     // cheapest first
        assertEquals(199.99, quotes.get(0).priceUsd(), 0.001);
    }

    @Test
    void slowTasks_areReportedAsTimeoutNotFailure() throws InterruptedException {
        TravelPortal portal = new TravelPortal(exec);
        List<TravelQuoteTask> tasks = List.of(
                new TravelQuoteTask("fast-air",  20,   199.00),
                new TravelQuoteTask("slow-jet",  500, 175.00),   // exceeds deadline
                new TravelQuoteTask("zoom-air",  20,   210.00));

        long start = System.nanoTime();
        List<TravelQuote> quotes = portal.getRankedQuotes(tasks, 200, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals(3, quotes.size());

        long okCount = quotes.stream().filter(q -> q.status().equals("ok")).count();
        long timeouts = quotes.stream().filter(q -> q.status().equals("timeout")).count();
        assertEquals(2, okCount);
        assertEquals(1, timeouts);

        List<String> companiesTimedOut = quotes.stream()
                .filter(q -> q.status().equals("timeout"))
                .map(TravelQuote::company)
                .collect(Collectors.toList());
        assertEquals(List.of("slow-jet"), companiesTimedOut);

        // Wall-clock bound: invokeAll won't keep us much past the deadline.
        // Add generous slop for CI variance, but it must NOT take 500ms (the slow task).
        assertTrue(elapsedMs < 450,
                () -> "invokeAll should respect deadline; took " + elapsedMs + "ms");
    }

    @Test
    void timedOutQuotes_sortAfterOkQuotes() throws InterruptedException {
        TravelPortal portal = new TravelPortal(exec);
        List<TravelQuoteTask> tasks = List.of(
                new TravelQuoteTask("slow",  500, 50.00),
                new TravelQuoteTask("fast",  20,  300.00));

        List<TravelQuote> quotes = portal.getRankedQuotes(tasks, 100, TimeUnit.MILLISECONDS);

        // "fast" should come first even though "slow" had the lower price —
        // status ordering puts ok ahead of timeout regardless of price.
        assertEquals("fast", quotes.get(0).company());
        assertEquals("ok", quotes.get(0).status());
        assertEquals("timeout", quotes.get(1).status());
    }
}
