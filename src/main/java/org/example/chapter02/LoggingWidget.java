package org.example.chapter02;

/**
 * Listing 2.7 - Code that Would Deadlock if Intrinsic Locks
 * Were Not Reentrant.
 *
 * LoggingWidget extends Widget. Both classes have a synchronized
 * doSomething() method. When LoggingWidget.doSomething() is called,
 * the thread acquires the lock on `this`. It then calls
 * super.doSomething(), which also requires the lock on `this`.
 *
 * Because intrinsic locks are REENTRANT, the thread already holds
 * the lock and can reacquire it (the acquisition count goes from 1 to 2).
 * Without reentrancy, this call would deadlock — the thread would
 * block waiting for a lock it already holds.
 *
 * Reentrancy is implemented by tracking the owning thread and an
 * acquisition count. Each synchronized entry increments the count;
 * each exit decrements it. The lock is released when the count
 * reaches zero.
 */
public class LoggingWidget extends Widget {

    @Override
    public synchronized void doSomething() {
        System.out.println(getClass().getSimpleName() + ": calling doSomething");
        super.doSomething(); // would deadlock without reentrant locks
    }
}
