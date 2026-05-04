package org.example.chapter06;

/**
 * A quote from one travel company. Stand-in for the book's TravelQuote.
 *
 * @param company  who quoted
 * @param priceUsd quoted price; 0 means a sentinel value (timed out, error)
 * @param status   "ok", "timeout", "error"
 */
public record TravelQuote(String company, double priceUsd, String status) {

    public static TravelQuote timedOut(String company) {
        return new TravelQuote(company, 0.0, "timeout");
    }

    public static TravelQuote error(String company) {
        return new TravelQuote(company, 0.0, "error");
    }
}
