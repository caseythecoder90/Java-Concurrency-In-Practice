package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the one-shot execution pattern (Listing 7.20).
 */
class MailCheckerTest {

    @Test
    void anyHostHasMail_returnsTrue() throws InterruptedException {
        boolean result = MailChecker.checkMail(
                Set.of("a", "b", "c"),
                2, TimeUnit.SECONDS,
                host -> host.equals("b"));
        assertTrue(result);
    }

    @Test
    void noHostHasMail_returnsFalse() throws InterruptedException {
        boolean result = MailChecker.checkMail(
                Set.of("a", "b", "c"),
                2, TimeUnit.SECONDS,
                host -> false);
        assertFalse(result);
    }

    @Test
    void runsHostsInParallel() throws InterruptedException {
        // 4 hosts, each predicate sleeps ~100ms. Sequential = 400ms,
        // parallel ≈ 100ms. Allow generous slop for CI.
        Set<String> hosts = Set.of("h1", "h2", "h3", "h4");
        long start = System.nanoTime();
        boolean result = MailChecker.checkMail(hosts, 2, TimeUnit.SECONDS, host -> {
            try { Thread.sleep(100); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return host.endsWith("3");
        });
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(result);
        assertTrue(elapsedMs < 350,
                () -> "expected parallel execution; took " + elapsedMs + "ms");
    }
}
