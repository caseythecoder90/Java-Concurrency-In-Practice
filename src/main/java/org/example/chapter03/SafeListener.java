package org.example.chapter03;

/**
 * Listing 3.8 - Using a Factory Method to Prevent the `this` Reference
 * from Escaping During Construction.
 *
 * The fix for {@link ThisEscape}:
 *   1. A PRIVATE constructor does only the internal setup — the listener
 *      is created but NOT registered yet.
 *   2. A PUBLIC STATIC FACTORY METHOD constructs the instance, then
 *      registers the listener AFTER construction has finished.
 *
 * By the time the listener is visible to the EventSource, the
 * SafeListener object is fully constructed.
 */
public class SafeListener {

    private final EventSource.EventListener listener;
    private final String data;

    private SafeListener() {
        this.data = "fully constructed";
        this.listener = e -> doSomething(e);
    }

    public static SafeListener newInstance(EventSource source) {
        SafeListener safe = new SafeListener();       // fully constructed
        source.registerListener(safe.listener);       // safe to publish now
        return safe;
    }

    void doSomething(EventSource.Event e) {
        System.out.println("SafeListener[data=" + data + "] got event: " + e.getMessage());
    }
}
