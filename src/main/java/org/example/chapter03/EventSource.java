package org.example.chapter03;

import java.util.ArrayList;
import java.util.List;

/**
 * Supporting class used by {@link ThisEscape} and {@link SafeListener}.
 * An EventSource accepts listeners and can fire events to them.
 */
public class EventSource {

    private final List<EventListener> listeners = new ArrayList<>();

    public synchronized void registerListener(EventListener listener) {
        listeners.add(listener);
    }

    public synchronized void fireEvent(Event event) {
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    public interface EventListener {
        void onEvent(Event event);
    }

    public static final class Event {
        private final String message;

        public Event(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
