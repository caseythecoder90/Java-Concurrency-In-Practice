package org.example.chapter04;

/**
 * Listing 4.5 - Mutable Point Class Similar to java.awt.Point.
 *
 * NOT thread-safe — the fields are public and mutable. Usable safely
 * only via instance confinement inside a thread-safe container that
 * copies points on the way in and out (e.g. {@link MonitorVehicleTracker}).
 */
public class MutablePoint {

    public int x;
    public int y;

    public MutablePoint() {
        this.x = 0;
        this.y = 0;
    }

    public MutablePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public MutablePoint(MutablePoint p) {
        this.x = p.x;
        this.y = p.y;
    }
}
