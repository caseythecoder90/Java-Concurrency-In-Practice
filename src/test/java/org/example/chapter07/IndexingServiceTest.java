package org.example.chapter07;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the poison-pill shutdown pattern (Listings 7.17, 7.18, 7.19).
 */
class IndexingServiceTest {

    @Test
    void naturalCompletion_indexesEveryFile() throws InterruptedException {
        List<String> files = List.of("a.txt", "b.txt", "c.txt", "d.txt");
        IndexingService svc = new IndexingService(files);
        svc.start();
        svc.awaitTermination();

        assertEquals(files, svc.indexed(),
                "in single-producer/single-consumer order should match");
    }

    @Test
    void stop_drainsQueueViaPoisonPillAndExits() throws InterruptedException {
        // Big list so the producer is mid-stream when we stop it.
        List<String> files = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) files.add("file-" + i);

        IndexingService svc = new IndexingService(files);
        svc.start();
        Thread.sleep(20);
        svc.stop();
        svc.awaitTermination();

        // Indexer should have exited cleanly (the await returned).
        // It may have processed any prefix of the input — we only require
        // that it stopped, not that it processed everything.
        List<String> indexed = svc.indexed();
        assertTrue(indexed.size() <= files.size());
        // None of the indexed entries should be the POISON sentinel.
        assertFalse(indexed.contains(IndexingService.POISON),
                "POISON sentinel must never reach indexFile()");
    }
}
