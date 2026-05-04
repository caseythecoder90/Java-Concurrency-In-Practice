package org.example.chapter06;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the page-renderer evolution: Sequential → Future →
 * CompletionService.
 *
 * The key behavioral test is on {@link CompletionRenderer}: render order
 * should be COMPLETION order, not submission order. We arrange the
 * downloads with descending latency so the first-submitted is slowest;
 * a CompletionService renders fast→slow, while Future-based renderers
 * preserve submission order.
 */
class RendererTest {

    private ExecutorService exec;

    @BeforeEach
    void setUp() {
        exec = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        exec.shutdown();
        assertTrue(exec.awaitTermination(2, TimeUnit.SECONDS));
    }

    // ---------------------------------------------------------------
    // Sequential renderer
    // ---------------------------------------------------------------

    @Test
    void sequentialRenderer_rendersTextThenImagesInSubmissionOrder() throws InterruptedException {
        SequentialRenderer renderer = new SequentialRenderer();
        List<ImageInfo> infos = List.of(
                new ImageInfo("a", 30),
                new ImageInfo("b", 30),
                new ImageInfo("c", 30));

        List<String> events = renderer.renderPage("hello", infos);

        assertEquals(List.of("text:hello", "img:a", "img:b", "img:c"), events);
    }

    // ---------------------------------------------------------------
    // Future-based renderer
    // ---------------------------------------------------------------

    @Test
    void futureRenderer_rendersTextThenImagesInSubmissionOrder() throws InterruptedException {
        FutureRenderer renderer = new FutureRenderer(exec);
        List<ImageInfo> infos = List.of(
                new ImageInfo("a", 50),
                new ImageInfo("b", 30),
                new ImageInfo("c", 10));

        List<String> events = renderer.renderPage("hello", infos);

        // Even though c finishes first, the bundled Future preserves submission order.
        assertEquals(List.of("text:hello", "img:a", "img:b", "img:c"), events);
    }

    // ---------------------------------------------------------------
    // CompletionService renderer
    // ---------------------------------------------------------------

    @Test
    void completionRenderer_rendersImagesInCompletionOrder() throws InterruptedException {
        CompletionRenderer renderer = new CompletionRenderer(exec);
        // Slowest first to first to make the test deterministic: the only way the
        // completion order matches submission order is if the renderer waits
        // for the slowest before emitting any.
        List<ImageInfo> infos = List.of(
                new ImageInfo("slow", 200),
                new ImageInfo("medium", 100),
                new ImageInfo("fast", 30));

        List<String> events = renderer.renderPage("hello", infos);

        assertEquals("text:hello", events.get(0), "text always renders first");
        // Drop the leading text event and verify image completion order: fast → medium → slow.
        List<String> imageEvents = events.subList(1, events.size());
        assertEquals(List.of("img:fast", "img:medium", "img:slow"), imageEvents);
    }

    @Test
    void completionRenderer_emitsExactlyOneEventPerImage() throws InterruptedException {
        CompletionRenderer renderer = new CompletionRenderer(exec);
        List<ImageInfo> infos = List.of(
                new ImageInfo("a", 20),
                new ImageInfo("b", 20),
                new ImageInfo("c", 20),
                new ImageInfo("d", 20));

        List<String> events = renderer.renderPage("p", infos);

        // 1 text event + N image events
        assertEquals(infos.size() + 1, events.size());
    }
}
