package org.example.chapter06;

/**
 * Stand-in for the book's {@code ImageInfo} type. The book leaves the
 * details abstract; here {@code downloadImage} simulates network latency
 * with a sleep, then returns an {@link ImageData} carrying this info's
 * label.
 *
 * Each ImageInfo has a deterministic {@code latencyMillis} so tests can
 * verify that the renderer that emits results in completion order
 * (CompletionService) really does — by giving early-submitted images a
 * long latency and late-submitted ones a short one, then checking the
 * render order.
 */
public class ImageInfo {

    private final String label;
    private final long latencyMillis;

    public ImageInfo(String label, long latencyMillis) {
        this.label = label;
        this.latencyMillis = latencyMillis;
    }

    public String label() {
        return label;
    }

    public ImageData downloadImage() throws InterruptedException {
        Thread.sleep(latencyMillis);
        return new ImageData(label);
    }
}
