package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the producer-consumer logging services in section 7.2.1
 * (Listings 7.15 and 7.16).
 */
class LogServiceTest {

    // -----------------------------------------------------------------
    // Listing 7.15 — LogService with reservation counter
    // -----------------------------------------------------------------

    @Test
    void logService_writesAllMessagesAndStopsCleanly() throws InterruptedException {
        StringWriter sink = new StringWriter();
        LogService logger = new LogService(sink, 1024);
        logger.start();

        for (int i = 0; i < 50; i++) {
            logger.log("msg-" + i);
        }
        logger.stop();
        logger.awaitTermination();

        String written = sink.toString();
        assertTrue(written.contains("msg-0"));
        assertTrue(written.contains("msg-49"));
        long count = written.lines().count();
        assertEquals(50, count, "every submitted message should be written exactly once");
    }

    @Test
    void logService_rejectsLogsAfterStop() throws InterruptedException {
        StringWriter sink = new StringWriter();
        LogService logger = new LogService(sink, 1024);
        logger.start();
        logger.log("first");
        logger.stop();
        logger.awaitTermination();

        assertThrows(IllegalStateException.class,
                () -> logger.log("after-stop"),
                "log() after stop() must reject submissions");
    }

    @Test
    void logService_drainsConcurrentlyEnqueuedMessages() throws InterruptedException {
        StringWriter sink = new StringWriter();
        LogService logger = new LogService(sink, 256);
        logger.start();

        int producers = 4;
        int perProducer = 25;
        AtomicInteger sent = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(producers);
        CountDownLatch go = new CountDownLatch(1);

        for (int p = 0; p < producers; p++) {
            int id = p;
            new Thread(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { return; }
                for (int i = 0; i < perProducer; i++) {
                    try {
                        logger.log("p" + id + "-" + i);
                        sent.incrementAndGet();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }).start();
        }

        ready.await();
        go.countDown();
        // Producers finish quickly; wait for them.
        Thread.sleep(200);
        logger.stop();
        logger.awaitTermination();

        long lines = sink.toString().lines().count();
        assertEquals(sent.get(), lines,
                "every successfully-submitted message should land in the sink");
    }

    // -----------------------------------------------------------------
    // Listing 7.16 — LogServiceExec (delegating to ExecutorService)
    // -----------------------------------------------------------------

    @Test
    void logServiceExec_writesAllMessagesAndShutsDownCleanly() throws InterruptedException {
        StringWriter sink = new StringWriter();
        LogServiceExec logger = new LogServiceExec(sink);
        logger.start();
        for (int i = 0; i < 20; i++) logger.log("m-" + i);
        logger.stop(2, TimeUnit.SECONDS);

        long lines = sink.toString().lines().count();
        assertEquals(20, lines);
    }

    @Test
    void logServiceExec_silentlyDropsMessagesAfterStop() throws InterruptedException {
        StringWriter sink = new StringWriter();
        LogServiceExec logger = new LogServiceExec(sink);
        logger.start();
        logger.log("before");
        logger.stop(2, TimeUnit.SECONDS);
        // After stop, log() catches RejectedExecutionException and drops the message.
        // No exception, no entry in the sink.
        logger.log("after");
        assertEquals(Set.of("before"), Set.of(sink.toString().strip()));
    }
}
