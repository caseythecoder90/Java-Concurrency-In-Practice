package org.example.chapter06;

/**
 * Stand-in for the book's {@code ImageData} type — the result of
 * downloading an image. We only need a label so tests can verify which
 * image was rendered when.
 */
public record ImageData(String label) {}
