package org.example.chapter06;

import java.util.ArrayList;
import java.util.List;

/**
 * Listing 6.10 - Rendering Page Elements Sequentially.
 *
 * Downloads images one at a time, then renders text, then renders images
 * in the order they were downloaded. The CPU is idle most of the time
 * waiting on network; total latency is the SUM of download times.
 *
 * The book's version uses concrete {@code renderText} and {@code renderImage}
 * methods; here they're encoded as entries in a returned list so the
 * tests can verify what was rendered, in what order. The semantic shape
 * is identical.
 */
public class SequentialRenderer {

    public List<String> renderPage(String text, List<ImageInfo> infos) throws InterruptedException {
        List<ImageData> imageData = new ArrayList<>();
        for (ImageInfo info : infos) {
            imageData.add(info.downloadImage());   // ← serial network I/O
        }

        List<String> events = new ArrayList<>();
        events.add("text:" + text);
        for (ImageData img : imageData) {
            events.add("img:" + img.label());
        }
        return events;
    }
}
