package org.example.chapter03;

/**
 * Listing 3.7 - Implicitly Allowing the `this` Reference to Escape. Don't Do This.
 *
 * Registering an inner-class listener from the constructor leaks `this`:
 * the inner EventListener holds an implicit reference to the enclosing
 * ThisEscape instance. Once registered, other threads can reach the
 * ThisEscape object BEFORE its constructor has finished — observing a
 * partially constructed instance.
 *
 * This is true even if the registration is the last statement in the
 * constructor.
 *
 * See SafeListener for the correct factory-method fix.
 */
public class ThisEscape {

    private final String data;

    public ThisEscape(EventSource source) {
        source.registerListener(new EventSource.EventListener() {
            @Override
            public void onEvent(EventSource.Event e) {
                doSomething(e);
            }
        });
        // `this` has already escaped above; the following assignment
        // may not be visible to the other thread holding the listener.
        this.data = "fully constructed";
    }

    void doSomething(EventSource.Event e) {
        System.out.println("ThisEscape[data=" + data + "] got event: " + e.getMessage());
    }
}
