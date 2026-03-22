# Chapter 2: Thread Safety

## Key Concepts

### What is Thread Safety?

Writing thread-safe code is fundamentally about managing access to **shared, mutable state**. An object's state is its data, stored in instance or static fields. Whether an object needs to be thread-safe depends on whether it will be accessed from multiple threads — this is a property of *how the object is used*, not what it does.

> **"A class is thread-safe if it behaves correctly when accessed from multiple threads, regardless of the scheduling or interleaving of the execution of those threads by the runtime environment, and with no additional synchronization or other coordination on the part of the calling code."** (p. 18)

The key phrase is "no additional coordination on the part of the calling code." A truly thread-safe class encapsulates all the synchronization it needs — callers should not have to worry about concurrency.

Thread-safe classes always produce **correct** behavior. Correctness means that a class conforms to its specification — its invariants and postconditions hold. A class that works correctly in a single-threaded environment and continues to work correctly when accessed from multiple threads (without the caller needing to do anything extra) is thread-safe.

There are three ways to fix shared mutable state problems:
1. **Don't share** the state variable across threads
2. **Make it immutable**
3. **Use synchronization** whenever accessing it

### Stateless Objects Are Always Thread-Safe

A **stateless** object has no fields and references no fields from other classes. All information it needs lives in local variables on the thread's stack, which are not shared. Since there is nothing to share, there's nothing that can go wrong.

> **"Stateless objects are always thread-safe."** (p. 19)

The book demonstrates this with `StatelessFactorizer` — a servlet that computes factors using only local variables. No matter how many threads call it simultaneously, they can't interfere with each other because they share no state.

### Atomicity

#### Race Conditions
Adding even a single piece of state (like a hit counter) can break thread safety. The increment operation `++count` looks atomic but is actually a **read-modify-write** compound action: (1) read the current value, (2) modify it, (3) write the new value. If two threads interleave these steps, updates are lost.

This is a **race condition** — the correctness of the computation depends on the relative timing of multiple threads. The most common type is **check-then-act**: you observe something to be true (check), then take action based on that observation (act), but between the check and the act, the observation may have become invalid.

> **"A race condition occurs when the correctness of a computation depends on the relative timing or interleaving of multiple threads by the runtime."** (p. 21)

**Lazy initialization** is the classic check-then-act race. If two threads simultaneously check whether an instance is `null`, both may see `null` and both may create separate instances — violating the invariant that there's only one.

> **"The most common type of race condition is check-then-act, where a potentially stale observation is used to make a decision on what to do next."** (p. 21)

#### Compound Actions
Operations like read-modify-write and check-then-act are **compound actions** — sequences of operations that must be executed atomically to be correct. They must appear indivisible from the perspective of any other thread.

> **"Operations A and B are atomic with respect to each other if, from the perspective of a thread executing A, when another thread executes B, either all of B has executed or none of it has. An atomic operation is one that is atomic with respect to all operations, including itself, that operate on the same state."** (p. 22)

The `java.util.concurrent.atomic` package provides **atomic variable classes** like `AtomicLong` that use hardware-level atomic instructions (CAS — compare-and-swap) to make compound actions like increment thread-safe without locks.

> **"Where practical, use existing thread-safe objects, like AtomicLong, to manage your class's state. It is simpler to reason about the possible states and state transitions for existing thread-safe objects than it is for arbitrary state variables, and this makes it easier to maintain and verify thread safety."** (p. 23)

### Locking

#### When One Atomic Variable Isn't Enough
Using a single `AtomicLong` for a counter is fine. But what happens when a class has **multiple related state variables**? If a servlet needs to cache both the last number and its factors, using two separate `AtomicReference` fields is **not** thread-safe — even though each individual field is atomic, the two updates are not atomic *with respect to each other*. A thread could see the new number but the old factors.

> **"To preserve state consistency, update related state variables in a single atomic operation."** (p. 24)

#### Intrinsic Locks (synchronized)
Java provides the `synchronized` block as a built-in locking mechanism. Every Java object can act as a lock — these are called **intrinsic locks** or **monitor locks**. A `synchronized` block has two parts: a reference to the object that serves as the lock, and a block of code guarded by that lock. A `synchronized` method uses `this` (or the `Class` object for static methods) as the lock.

Intrinsic locks are **mutexes** (mutual exclusion locks) — at most one thread can hold the lock at a time. When thread A tries to acquire a lock held by thread B, A must **wait** (block) until B releases it. If B never releases, A waits forever.

#### Reentrancy
Intrinsic locks are **reentrant** — if a thread tries to acquire a lock it already holds, the request succeeds. This means a `synchronized` method can call another `synchronized` method on the same object without deadlocking. Reentrancy is implemented by associating a lock with an acquisition count and the owning thread. When the count goes to zero, the lock is released.

> **"Reentrancy saves us from deadlock in situations where a thread tries to acquire a lock that it already holds."** (p. 27)

Without reentrancy, a subclass calling `super.doSomething()` from a `synchronized` method would deadlock, because the parent's `synchronized doSomething()` would try to acquire the same lock the child already holds.

### Guarding State with Locks

A lock ensures that only one thread at a time executes the guarded code. But locking is about more than mutual exclusion — it's about **memory visibility** and **protecting invariants**. When multiple variables participate in an invariant (like "lastNumber and lastFactors must correspond"), they must all be guarded by the **same lock**.

> **"For each mutable state variable that may be accessed by more than one thread, all accesses to that variable must be performed with the same lock held. In this case, we say that the variable is guarded by that lock."** (p. 28)

> **"Every shared, mutable variable should be guarded by exactly one lock. Make it clear to maintainers which lock that is."** (p. 28)

> **"For every invariant that involves more than one variable, all the variables involved in that invariant must be guarded by the same lock."** (p. 29)

### Liveness and Performance

Wrapping an entire servlet method in `synchronized` is safe but creates a severe **bottleneck** — only one thread can execute the method at a time. The servlet effectively becomes single-threaded, defeating the purpose of a multi-threaded servlet container.

> **"There is frequently a tension between simplicity and performance. When implementing a synchronization policy, resist the temptation to prematurely sacrifice simplicity (potentially compromising safety) for the sake of performance."** (p. 31)

> **"Avoid holding locks during lengthy computations or operations at risk of not completing quickly such as network or console I/O."** (p. 32)

The solution is to **narrow the scope of synchronization** — only hold the lock while accessing shared mutable state. The book's `CachedFactorizer` (Listing 2.8) demonstrates this: it uses `synchronized` blocks just for the checks and updates, but performs the expensive factorization outside any lock. This balances thread safety with concurrency.

## Gotchas & Pitfalls
- A class with no state is automatically thread-safe — but adding even a single mutable field changes everything.
- Two individually-atomic operations are NOT automatically atomic together. `AtomicReference` for field A + `AtomicReference` for field B ≠ atomic update of A and B together.
- `synchronized` on the entire method is the simplest fix but can kill concurrency. Narrow your synchronized blocks to the minimum code that actually accesses shared state.
- Check-then-act race conditions (lazy init, "put-if-absent") are subtle because each individual step may be thread-safe on its own — the hazard is in the gap between them.
- Reentrancy is often invisible — you rely on it whenever a synchronized method calls another synchronized method on the same object, including via inheritance.
- Holding a lock during I/O or long computations is a performance antipattern — other threads are blocked for the entire duration.

## Page References
- Definition of thread safety: p. 18
- Stateless objects are thread-safe: p. 19
- Atomicity, race conditions, check-then-act: pp. 20-22
- Compound actions and AtomicLong: pp. 22-23
- Intrinsic locks and synchronized: pp. 24-27
- Reentrancy: pp. 26-27
- Guarding state with locks: pp. 27-29
- Liveness and performance: pp. 29-32
