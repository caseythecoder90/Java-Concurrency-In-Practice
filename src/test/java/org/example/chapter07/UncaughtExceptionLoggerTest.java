package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UncaughtExceptionLogger} (Listing 7.25).
 *
 * Doesn't try to test the JVM's UncaughtExceptionHandler dispatch — just
 * verifies that the handler logs at SEVERE with the cause attached.
 */
class UncaughtExceptionLoggerTest {

    @Test
    void uncaughtException_logsSevereWithCause() throws InterruptedException {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getLogger(UncaughtExceptionLoggerTest.class.getName());
        Handler captured = new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        captured.setLevel(Level.ALL);
        logger.addHandler(captured);
        logger.setLevel(Level.ALL);

        try {
            UncaughtExceptionLogger handler = new UncaughtExceptionLogger(logger);
            Thread t = new Thread(() -> { throw new IllegalStateException("oops"); });
            t.setUncaughtExceptionHandler(handler);
            t.start();
            t.join(1_000);

            assertEquals(1, records.size());
            LogRecord rec = records.get(0);
            assertEquals(Level.SEVERE, rec.getLevel());
            assertNotNull(rec.getThrown());
            assertInstanceOf(IllegalStateException.class, rec.getThrown());
            assertEquals("oops", rec.getThrown().getMessage());
        } finally {
            logger.removeHandler(captured);
        }
    }
}
