package org.example.chapter04;

/**
 * Listing 4.3 - Guarding State with a Private Lock.
 *
 * Uses a PRIVATE Object as the lock instead of `this` or a publicly
 * accessible lock. Advantages:
 *   - Client code cannot acquire the lock, so clients can't break the
 *     synchronization policy or cause liveness issues.
 *   - The locking policy can be verified by examining this class
 *     alone — no need to examine the entire program.
 *   - The lock is encapsulated alongside the state it guards.
 */
public class PrivateLock {

    private final Object myLock = new Object();
    private Widget widget;

    public void setWidget(Widget w) {
        synchronized (myLock) {
            this.widget = w;
        }
    }

    public Widget getWidget() {
        synchronized (myLock) {
            return widget;
        }
    }

    public static final class Widget {
        private final String name;

        public Widget(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
