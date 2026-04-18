package org.example.chapter03;

/**
 * Listing 3.6 - Allowing Internal Mutable State to Escape. Don't Do This.
 *
 * The states array is declared private, but getStates() returns the
 * array reference directly. Any caller can now mutate the underlying
 * state — what was supposed to be private has been effectively made
 * public. The array has "escaped" its intended scope.
 *
 * The fix is to return a defensive copy, or to store the abbreviations
 * in an unmodifiable List (e.g. List.of(...)).
 */
public class UnsafeStates {

    private final String[] states = { "AK", "AL", "AZ", "AR", "CA" };

    public String[] getStates() {
        return states; // ← escape!
    }
}
