package org.example.chapter02;

/**
 * Listing 2.3 - Race Condition in Lazy Initialization.
 *
 * This is the classic check-then-act race condition. Two threads can
 * simultaneously observe that instance is null, and both create a
 * new ExpensiveObject — violating the invariant that only one
 * instance should exist.
 *
 * Thread A: checks instance == null → true
 * Thread B: checks instance == null → true (A hasn't written yet)
 * Thread A: creates and assigns instance
 * Thread B: creates and assigns a DIFFERENT instance (overwrites A's)
 */
public class LazyInitRace {

    private ExpensiveObject instance;

    public ExpensiveObject getInstance() {
        if (instance == null) {           // check
            instance = new ExpensiveObject(); // act
        }
        return instance;
    }

    /**
     * A placeholder for an object that is expensive to construct,
     * motivating lazy initialization.
     */
    public static class ExpensiveObject {
        public ExpensiveObject() {
            // Simulate expensive construction
        }
    }
}
