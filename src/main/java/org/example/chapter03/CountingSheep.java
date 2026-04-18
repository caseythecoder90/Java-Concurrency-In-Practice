package org.example.chapter03;

/**
 * Listing 3.4 - Counting Sheep (volatile as a completion/status flag).
 *
 * A classic use of volatile: a status flag flipped by one thread and
 * observed by another. Without `volatile`, the server JVM is allowed
 * to hoist the flag out of the loop and turn it into an infinite loop.
 *
 * This usage pattern matches the three-part checklist for volatile:
 *   1. Writes don't depend on the current value.
 *   2. The variable does not participate in invariants with other state.
 *   3. Locking isn't required for any other reason.
 */
public class CountingSheep {

    private volatile boolean asleep;

    public void fallAsleep() {
        asleep = true;
    }

    public void countSheep() {
        int sheep = 0;
        while (!asleep) {
            sheep++;
            // countSomeSheep();
        }
        System.out.println("Counted " + sheep + " sheep before falling asleep.");
    }
}
