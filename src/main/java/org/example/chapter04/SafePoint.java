package org.example.chapter04;

/**
 * Listing 4.11 - Thread-safe Mutable Point Class.
 *
 * Thread-safe by confining both fields under its own intrinsic lock.
 * Returns an `int[]{x, y}` from get() so callers read both
 * coordinates atomically — separate getX/getY could let a caller
 * observe an (x, y) the point never actually held.
 *
 * The private constructor `SafePoint(int[] a)` exists to enable the
 * PRIVATE CONSTRUCTOR CAPTURE IDIOM (Bloch and Gafter):
 *   - A naive copy constructor `this(p.x, p.y)` would race between
 *     the two field reads.
 *   - Instead, the public copy constructor calls `this(p.get())`,
 *     delegating to the private array-based constructor and doing
 *     a SINGLE atomic snapshot of both coordinates first.
 */
public class SafePoint {

    private int x;
    private int y;

    private SafePoint(int[] a) {
        this(a[0], a[1]);
    }

    public SafePoint(SafePoint p) {
        this(p.get());
    }

    public SafePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public synchronized int[] get() {
        return new int[] { x, y };
    }

    public synchronized void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
