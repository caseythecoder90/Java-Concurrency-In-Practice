package org.example.chapter04;

/**
 * Listing 4.6 - Immutable Point Class Used by DelegatingVehicleTracker.
 *
 * Immutable: all fields final, no mutators, no escape of `this`.
 * Because it's immutable, Point can be freely shared across threads
 * — no locking, no defensive copies.
 */
public final class Point {

    public final int x;
    public final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
