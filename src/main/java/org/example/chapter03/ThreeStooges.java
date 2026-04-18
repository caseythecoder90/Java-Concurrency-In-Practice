package org.example.chapter03;

import java.util.HashSet;
import java.util.Set;

/**
 * Listing 3.11 - Immutable Class Built Out of Mutable Underlying Objects.
 *
 * ThreeStooges is immutable even though its internal HashSet is mutable:
 *   - All state is established in the constructor.
 *   - The `stooges` reference is final (unchangeable).
 *   - The HashSet is never exposed to callers.
 *   - No method modifies the set after construction.
 *   - `this` does not escape during construction.
 *
 * Because all three immutability requirements are met — unmodifiable
 * state, all fields final, proper construction — ThreeStooges is
 * inherently thread-safe and can be freely shared and published.
 */
public final class ThreeStooges {

    private final Set<String> stooges = new HashSet<>();

    public ThreeStooges() {
        stooges.add("Moe");
        stooges.add("Larry");
        stooges.add("Curly");
    }

    public boolean isStooge(String name) {
        return stooges.contains(name);
    }
}
