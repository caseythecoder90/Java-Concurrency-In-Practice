package org.example.chapter06;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Listing 6.17 - Requesting Travel Quotes Under a Time Budget.
 *
 * Submits a list of quote tasks with a single deadline. After the
 * deadline, any unfinished task is cancelled by invokeAll(); its Future
 * has isCancelled()==true and Future.get() throws CancellationException.
 *
 * Returned Futures are in submission order — not completion order.
 * Iterate and decide per-Future:
 *
 *   - completed normally → keep the result
 *   - cancelled (timed out) → record a sentinel "timed out" quote
 *   - threw → record a sentinel "error" quote
 *
 * This is the canonical "ask N services, give them all the same total
 * deadline, return whatever came back" pattern.
 */
public class TravelPortal {

    private final ExecutorService executor;

    public TravelPortal(ExecutorService executor) {
        this.executor = executor;
    }

    public List<TravelQuote> getRankedQuotes(List<TravelQuoteTask> tasks,
                                              long deadline, TimeUnit unit)
            throws InterruptedException {

        List<Future<TravelQuote>> futures = executor.invokeAll(tasks, deadline, unit);

        List<TravelQuote> quotes = new ArrayList<>(tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            TravelQuoteTask task = tasks.get(i);
            Future<TravelQuote> f = futures.get(i);
            try {
                quotes.add(f.isCancelled()
                        ? TravelQuote.timedOut(task.company())
                        : f.get());
            } catch (CancellationException ce) {
                quotes.add(TravelQuote.timedOut(task.company()));
            } catch (ExecutionException ee) {
                quotes.add(TravelQuote.error(task.company()));
            }
        }
        quotes.sort((a, b) -> {
            if (a.status().equals("ok") && !b.status().equals("ok")) return -1;
            if (!a.status().equals("ok") && b.status().equals("ok")) return 1;
            return Double.compare(a.priceUsd(), b.priceUsd());
        });
        return quotes;
    }
}
