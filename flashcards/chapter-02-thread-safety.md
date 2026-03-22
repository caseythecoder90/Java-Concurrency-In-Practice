# Chapter 2: Thread Safety — Flashcards

Q: What is thread safety? Give the book's definition.
A: A class is thread-safe if it behaves correctly when accessed from multiple threads, regardless of scheduling or interleaving, and with **no additional synchronization or coordination on the part of the calling code**. The key is that callers shouldn't need to do anything extra.

Q: What is "shared, mutable state" and why does it matter for thread safety?
A: An object's state is its data (instance/static fields). "Shared" means accessible by multiple threads; "mutable" means it can change. Thread safety is fundamentally about managing access to shared, mutable state — if state isn't shared or isn't mutable, there's no thread safety problem.

Q: What are the three ways to fix a shared mutable state problem?
A: 1) **Don't share** the variable across threads. 2) **Make it immutable**. 3) **Use synchronization** whenever accessing it.

Q: Why are stateless objects always thread-safe?
A: A stateless object has no fields — all data lives in local variables on the thread's stack, which are inherently thread-private. Since threads share nothing, they can't interfere with each other.

Q: What is a race condition?
A: A race condition occurs when the correctness of a computation depends on the relative timing or interleaving of multiple threads. The program may work most of the time but fail unpredictably when threads interleave in an unlucky order.

Q: What is the most common type of race condition? Give an example.
A: **Check-then-act**: you observe something (check) then take action based on that observation (act), but between the two, the observation may have become invalid. Classic example: **lazy initialization** — two threads both check `if (instance == null)`, both see null, and both create separate instances.

Q: What is a compound action? Give two examples.
A: A compound action is a sequence of operations that must execute atomically to be correct. Two common patterns: **read-modify-write** (e.g., `count++`) and **check-then-act** (e.g., lazy initialization). From any other thread's perspective, the compound action must appear indivisible.

Q: How does `AtomicLong` provide thread safety without locks?
A: `AtomicLong` uses hardware-level **compare-and-swap (CAS)** instructions to make compound actions like increment atomic. It's part of `java.util.concurrent.atomic` and is simpler to reason about than explicit locking for single-variable state.

Q: Why can't you just use two separate `AtomicReference` fields to protect two related state variables?
A: Each `AtomicReference` is individually atomic, but the two updates are **not atomic with respect to each other**. A thread could see the new value of one field paired with the stale value of the other, violating invariants. Related state variables must be updated in a **single atomic operation**.

Q: What is an intrinsic lock (monitor lock)?
A: Every Java object can act as a lock for `synchronized` blocks. These built-in locks are called **intrinsic locks** or **monitor locks**. A `synchronized` method uses `this` as the lock (or the `Class` object for static methods). They are mutexes — at most one thread can hold the lock at a time.

Q: What does it mean that intrinsic locks are reentrant? Why does this matter?
A: A reentrant lock allows a thread that already holds the lock to acquire it again without deadlocking. This matters for **inheritance**: if a subclass overrides a `synchronized` method and calls `super.method()`, the parent's synchronized method needs the same lock. Without reentrancy, this would deadlock.

Q: What is the rule for guarding state with locks?
A: For each mutable state variable accessed by more than one thread, **all** accesses must use the **same lock**. For invariants involving multiple variables, **all** those variables must be guarded by the **same** lock. Make it clear to maintainers which lock guards which variable.

Q: Why is synchronizing an entire servlet method a bad idea, even though it's thread-safe?
A: It creates a bottleneck — only one thread can execute the method at a time, making the servlet effectively single-threaded. This defeats the purpose of a multi-threaded container. The fix is to **narrow synchronization scope** to just the code that accesses shared state.

Q: What is the book's advice about holding locks during long operations?
A: Avoid holding locks during lengthy computations or operations that might not complete quickly, such as network or console I/O. Other threads are blocked for the entire duration, destroying concurrency.

Q: What does `CachedFactorizer` (Listing 2.8) demonstrate about balancing safety and performance?
A: It uses short `synchronized` blocks for checking and updating the cached state, but performs the expensive factorization **outside** any lock. This narrow synchronization scope preserves thread safety while allowing concurrent request processing.