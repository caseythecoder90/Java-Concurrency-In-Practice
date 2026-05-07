package org.example.chapter07;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Listing 7.20 - One-shot Execution Service.
 *
 * When you have a batch of tasks and you don't return until they're all
 * done (or until the deadline expires), the simplest service-lifecycle
 * management is no service at all: create a private Executor, scope its
 * lifetime to the method call, shut it down in finally.
 *
 * The book's version checks several mail hosts in parallel; this version
 * is parameterised so tests can supply a deterministic predicate (no
 * actual network calls).
 *
 * Why AtomicBoolean instead of {@code volatile boolean}: the lambda
 * captures a local variable, which has to be effectively final in Java.
 * AtomicBoolean keeps the field reference final while letting tasks
 * mutate the boolean.
 */
public final class MailChecker {

    private MailChecker() {}

    public static boolean checkMail(Set<String> hosts,
                                    long timeout,
                                    TimeUnit unit,
                                    Predicate<String> hasMail) throws InterruptedException {

        ExecutorService exec = Executors.newCachedThreadPool();
        AtomicBoolean hasNewMail = new AtomicBoolean(false);
        try {
            for (String host : hosts) {
                exec.execute(() -> {
                    if (hasMail.test(host)) {
                        hasNewMail.set(true);
                    }
                });
            }
        } finally {
            exec.shutdown();
            exec.awaitTermination(timeout, unit);
        }
        return hasNewMail.get();
    }
}
