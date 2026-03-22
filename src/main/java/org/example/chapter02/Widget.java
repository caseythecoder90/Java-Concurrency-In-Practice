package org.example.chapter02;

/**
 * Base class used by {@link LoggingWidget} to demonstrate lock reentrancy.
 *
 * The synchronized doSomething() method acquires the intrinsic lock on
 * `this`. When a subclass calls super.doSomething() from its own
 * synchronized method, reentrancy allows the same thread to acquire
 * the lock a second time without deadlocking.
 */
public class Widget {

    public synchronized void doSomething() {
        System.out.println(getClass().getSimpleName() + ": Widget.doSomething");
    }
}
