package org.example.chapter04;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Listing 4.9 - Delegating Thread Safety to Multiple Underlying State Variables.
 *
 * VisualComponent delegates to TWO thread-safe lists. This works
 * because the two listener lists are INDEPENDENT — VisualComponent
 * imposes no invariants across them. Each list is individually
 * thread-safe, and nothing about one list's state constrains the
 * other's, so delegation to each is sufficient.
 *
 * CopyOnWriteArrayList is particularly well-suited to listener lists
 * (frequent iteration, rare mutation — see Section 5.2.3).
 */
public class VisualComponent {

    private final List<KeyListener> keyListeners = new CopyOnWriteArrayList<>();
    private final List<MouseListener> mouseListeners = new CopyOnWriteArrayList<>();

    public void addKeyListener(KeyListener listener) {
        keyListeners.add(listener);
    }

    public void addMouseListener(MouseListener listener) {
        mouseListeners.add(listener);
    }

    public void removeKeyListener(KeyListener listener) {
        keyListeners.remove(listener);
    }

    public void removeMouseListener(MouseListener listener) {
        mouseListeners.remove(listener);
    }

    public List<KeyListener> getKeyListeners() {
        return keyListeners;
    }

    public List<MouseListener> getMouseListeners() {
        return mouseListeners;
    }

    public interface KeyListener {
        void onKey(char c);
    }

    public interface MouseListener {
        void onClick(int x, int y);
    }
}
