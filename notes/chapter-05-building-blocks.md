# Chapter 5: Building Blocks

## Key Concepts

Chapters 2-4 built the theory. Chapter 5 surveys the concrete tools `java.util.concurrent` gives you: thread-safe collections, blocking queues, synchronizers, and a capstone **scalable result cache** that pulls them all together. This is the pragmatic chapter — by the end you should know *which tool to reach for when*.

### 5.1 Synchronized Collections

The "legacy" thread-safe collections — `Vector`, `Hashtable`, and the `Collections.synchronizedXxx(...)` wrappers added in JDK 1.2. They achieve thread safety by **synchronizing every public method on a single lock (the collection itself)**.

#### Problem 1 — Compound Actions Aren't Atomic

Being thread-safe does *not* mean immune to all races. Any **compound action** — iterate, navigate, conditional put — still needs external synchronization:

```java
public static Object getLast(Vector list) {
    int lastIndex = list.size() - 1;
    return list.get(lastIndex);   // ← may throw AIOOBE if list shrank
}
```

This does not *corrupt* the `Vector`. `Vector` is behaving per spec — you asked for element `lastIndex`, it's gone, exception thrown. The compound `size()`-then-`get()` is the race.

The fix: **client-side locking on the collection itself** (the wrapper's intrinsic lock is the documented lock):

```java
synchronized (list) {
    int lastIndex = list.size() - 1;
    return list.get(lastIndex);
}
```

Same applies to iteration by index:

```java
for (int i = 0; i < vector.size(); i++)
    doSomething(vector.get(i));   // ← can throw AIOOBE
```

Wrap the whole loop in `synchronized (vector)` or you're in trouble.

#### Problem 2 — Iterators and `ConcurrentModificationException`

Even the for-each loop (which desugars to `Iterator`) on a synchronized collection can throw `ConcurrentModificationException` if another thread mutates the collection mid-iteration. These iterators are **fail-fast**:

- Implemented via a **modification counter** on the collection.
- If `hasNext` / `next` see the counter change, they throw CME.
- The check is done **without synchronization** — it can miss concurrent modifications (stale read of the counter) — deliberate, to reduce overhead. Treat CME as an **early warning**, not a guarantee.
- CME can even arise in single-threaded code if you remove from the collection directly instead of through `Iterator.remove`.

Two fixes for iteration:
1. **Lock for the duration of iteration.** Safe but kills concurrency; also calling a user method under a lock is a deadlock risk factor.
2. **Clone-then-iterate.** Hold the lock only long enough to clone; iterate the clone. Costs memory but avoids holding the lock over user code.

> **"The longer a lock is held, the more likely it is to be contended."** (p. 86)

#### Problem 3 — Hidden Iterators (Listing 5.6)

Iteration isn't always a `for` loop. These all iterate internally:

- `toString` — so does `"DEBUG: added ten elements to " + set`, via `StringBuilder.append(Object)`.
- `hashCode` / `equals` — when the collection is used as a key/element of another collection.
- `containsAll`, `removeAll`, `retainAll`.
- Collection constructors that take another collection.

In `HiddenIterator` a `println(... + set)` iterates `set` without its lock — boom, CME.

> **"The greater the distance between the state and the synchronization that guards it, the more likely that someone will forget to use proper synchronization."** (p. 84)
>
> **"Encapsulating an object's synchronization makes it easier to enforce its synchronization policy."** (p. 84)

Wrap the `HashSet` with `Collections.synchronizedSet` and the iteration-through-`toString` problem vanishes.

### 5.2 Concurrent Collections

Java 5 introduced concurrent collections designed for concurrent access, not just thread safety via serialization. **Replacing synchronized collections with concurrent collections usually buys scalability with little risk.**

| Concurrent | Replaces |
|---|---|
| `ConcurrentHashMap` | `Hashtable` / `synchronizedMap` |
| `ConcurrentSkipListMap` / `ConcurrentSkipListSet` | sorted `synchronizedMap`/`Set` (Java 6) |
| `CopyOnWriteArrayList` / `CopyOnWriteArraySet` | synchronized `List`/`Set` for iteration-heavy workloads |
| `Queue`, `ConcurrentLinkedQueue`, `PriorityQueue` | — (new in Java 5) |
| `BlockingQueue` and implementations | — (new in Java 5) |
| `Deque` / `BlockingDeque` (Java 6) | — |

#### ConcurrentHashMap
Uses **lock striping** (Section 11.4.3) — many readers concurrent with each other and with a bounded number of writers. Far better throughput than a single big lock, with little penalty on single-threaded access.

Three key differences from `Hashtable`/`synchronizedMap`:
1. **Weakly consistent iterators** (not fail-fast): tolerate concurrent modification, traverse elements as they existed when the iterator was constructed, may or may not reflect later modifications. **No more CME.**
2. **Weakened bulk operations**: `size` and `isEmpty` may return approximations, because the real answer is a moving target under concurrency anyway.
3. **No client-lockable exclusive access.** You cannot `synchronized (map)` and be safe — the map locks internally. The tradeoff: you can't do client-side locking to build extra atomic operations on top of it. Instead, use the interface's built-in compound operations:

```java
public interface ConcurrentMap<K,V> extends Map<K,V> {
    V putIfAbsent(K key, V value);
    boolean remove(K key, V value);                    // remove-if-equal
    boolean replace(K key, V oldValue, V newValue);    // replace-if-equal
    V replace(K key, V newValue);
}
```

> **"Only if your application needs to lock the map for exclusive access is ConcurrentHashMap not an appropriate drop-in replacement."** (p. 86)

#### CopyOnWriteArrayList
Thread safety by **re-publishing a new immutable snapshot** of the backing array on every mutation. Iterators hold a reference to the snapshot that was current when they were created, so:
- **No locking and no CME during iteration.**
- Iterators see the elements as they were at creation time, regardless of later changes.

Cost: every mutation allocates and copies the whole backing array. **Only reasonable when iteration vastly outnumbers mutation** — the textbook case is event listener lists.

### 5.3 Blocking Queues and the Producer-Consumer Pattern

**BlockingQueue** adds `put` / `take` (and timed `offer` / `poll`) on top of `Queue`. If the queue is empty, `take` blocks until an element is available; if a bounded queue is full, `put` blocks until space is available.

This enables the **producer-consumer pattern**: separate *identifying* work from *executing* it by putting work items on a shared "to do" list. Benefits:

- **Decouples** producer and consumer code — neither has to know about the other, their count, or their rate.
- **Flow control** via blocking — a backed-up queue naturally throttles producers.
- **Performance**: a CPU-bound consumer and an I/O-bound producer can run concurrently for better overall throughput.
- **Resource management**: **bounded** queues stop producers from OOMing the process when consumers fall behind.

> **"Bounded queues are a powerful resource management tool for building reliable applications: they make your program more robust to overload by throttling activities that threaten to produce more work than can be handled."** (p. 89)

The classic dish-rack analogy: washer places dishes; dryer takes them; neither cares who the other is or how many of them there are.

#### Implementations
| Implementation | Character |
|---|---|
| `LinkedBlockingQueue` | FIFO, linked-list-backed |
| `ArrayBlockingQueue` | FIFO, array-backed |
| `PriorityBlockingQueue` | ordered (natural order or `Comparator`) |
| `SynchronousQueue` | **zero storage** — direct handoff |

`SynchronousQueue` isn't really a queue — it keeps no elements, just matched pairs of waiting producers/consumers. `put` blocks until a consumer is ready; `take` blocks until a producer is ready. Lower latency (no staging) and natural backpressure (producer knows the instant a consumer owns the work). Only suitable when consumers are plentiful enough to keep up.

#### Desktop Search Example (Listings 5.8-5.9)
A `FileCrawler` producer recursively walks directories and `put`s files onto a `LinkedBlockingQueue<File>`. Many `Indexer` consumers `take` files off and index them. Clean separation; the blocking queue is the only coupling.

#### Serial Thread Confinement
Blocking queues provide **safe publication** from producer to consumer. For **mutable** objects this supports a pattern called **serial thread confinement**: ownership transfers from one thread to another via the queue.

- The producer owns the object, mutates it freely.
- It publishes the object via `put` (safe publication).
- The consumer takes it — now **it** owns it and can mutate it.
- The producer must **never touch the object again** after handoff.

This is how object pools work: the pool publishes an object safely to a borrower; the borrower uses it exclusively and must not touch it after returning it.

#### Deques and Work Stealing (Java 6)
`Deque` / `BlockingDeque` (`ArrayDeque`, `LinkedBlockingDeque`): double-ended queues.

**Work stealing** pattern: each worker has **its own deque**. Normally works on its own deque (contention-free). When empty, it steals work from the **tail** of another worker's deque (far end → less contention with the owner, who works at the head). Great for problems where processing a unit of work produces more work (web crawlers, graph traversal, GC heap marking).

### 5.4 Blocking and Interruptible Methods

A thread **blocks** when it must wait for an external event it doesn't control: I/O, lock acquisition, `Thread.sleep`, another thread's result. The JVM moves it to `BLOCKED`, `WAITING`, or `TIMED_WAITING`. When the event occurs it goes back to `RUNNABLE`.

`InterruptedException` on a method signature is a signal: **this method is blocking**, and if you call `Thread.interrupt()` on the thread that's blocked in it, it will try to stop blocking early.

#### Interruption is cooperative

- Each thread has a boolean **interrupted flag**. `Thread.interrupt()` sets it.
- Blocking library methods check the flag and throw `InterruptedException`, clearing the flag.
- Nothing forces a thread to respond — it's a **polite request**. The typical use is cancellation.

#### Handling `InterruptedException` in Your Own Code
Two acceptable strategies:

1. **Propagate**: re-throw or don't catch. Let the exception flow to a caller that knows what to do.
2. **Restore the interrupt**: if your method signature can't declare `InterruptedException` (e.g. you're inside a `Runnable.run`), catch it and call `Thread.currentThread().interrupt()` so callers higher up still see the interrupt flag.

```java
// Listing 5.10
public void run() {
    try {
        processTask(queue.take());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();    // restore the flag
    }
}
```

**Do not swallow it** (catch-and-ignore) unless you are yourself `Thread` and control all the code up-stack. Swallowing destroys evidence of the interrupt and breaks cancellation.

### 5.5 Synchronizers

A **synchronizer** is any object that coordinates control flow of threads based on its state. Shared traits:
- Encapsulates state determining whether arriving threads pass or wait.
- Methods to mutate that state.
- Methods to wait efficiently for the desired state.

Blocking queues, latches, semaphores, barriers, and `FutureTask` are all synchronizers.

#### Latches — `CountDownLatch`
A **latch** delays thread progress until it reaches a **terminal state**, then lets everyone through forever. One-shot — not reusable.

`CountDownLatch(n)`: threads call `await()` to block; others call `countDown()`; the latch opens when the counter hits zero.

Uses:
- Ensure a computation can't proceed until resources are initialized.
- Service dependency ordering at startup.
- Wait until every participant is ready, then release.

`TestHarness` (Listing 5.11) shows **starting gate + ending gate** for accurate concurrent benchmarking:
- Start gate count = 1; all workers `await()` it. The master counts it down to release them simultaneously (no "head start" for threads created earlier).
- End gate count = N (worker count); each worker counts down on completion. The master `await()`s it to block until the last worker finishes.

#### `FutureTask`
`FutureTask` is a result-bearing computation (built on `Callable<V>`) that acts like a **latch**. Three states: waiting, running, completed. Completion covers normal return, exception, or cancellation. Once completed, stays there.

`Future.get()`:
- Returns the result immediately if completed.
- Blocks until completion otherwise.
- **Provides safe publication** of the result from the computing thread to the consuming thread.

`Preloader` (Listing 5.12): spawns a `Thread` running a `FutureTask<ProductInfo>` early, so when the program later needs it, `future.get()` usually returns immediately. Note: the thread is **started in `start()`**, not the constructor — don't start threads from constructors (Chapter 3 rule about `this` escaping).

#### `launderThrowable` (Listing 5.13)
`Future.get()` wraps anything the task throws — checked, unchecked, `Error` — in `ExecutionException`, and the real cause is returned as `Throwable` (awkward). The pattern:

```java
public static RuntimeException launderThrowable(Throwable t) {
    if (t instanceof RuntimeException) return (RuntimeException) t;
    else if (t instanceof Error) throw (Error) t;
    else throw new IllegalStateException("Not unchecked", t);
}
```

Callers first test for and rethrow any known **checked** exceptions from their `Callable`, then hand the rest to `launderThrowable` and throw its result.

#### Semaphores
A **counting semaphore** controls how many threads may access a resource or perform an action. Initialized with `N` virtual permits:
- `acquire()` consumes a permit (blocks if none available).
- `release()` returns one.
- A `Semaphore(1)` is a **binary semaphore** / non-reentrant mutex.

Note: permits aren't associated with threads — one thread can `release` a permit another thread `acquire`d. The semaphore isn't even limited to its initial count; `acquire` just "consumes" and `release` just "creates."

`BoundedHashSet` (Listing 5.14): wraps a `HashSet`, uses a `Semaphore(bound)` to block `add` when full. Releases the permit only if the add actually happened; `remove` releases a permit if removal succeeded.

Prime use cases: **resource pools** (DB connection pools), **bounded collections**, **rate limiters**.

#### Barriers — `CyclicBarrier`
A **barrier** blocks a group of threads until all have arrived, then releases them together. **Reusable** (unlike a latch).

> **"Latches are for waiting for events; barriers are for waiting for other threads."** (p. 99)

`CyclicBarrier(n)`: threads call `await()`; when all `n` have arrived, all are released, the barrier resets, and the cycle can repeat. If `await` times out or a waiter is interrupted, the barrier breaks and all pending `await`s throw `BrokenBarrierException`.

Optional `barrierAction`: a `Runnable` executed on one of the subtask threads right before the parties are released (perfect for "commit the results of this step" logic).

`CellularAutomata` (Listing 5.15): partition the board into `Ncpu` sub-boards, one worker thread per partition. Each worker computes new values for its part, then `barrier.await()`. The barrier action commits the step's new values; then all workers are released to compute the next step. The rule of thumb for CPU-bound work with no I/O and no shared data: `Ncpu` or `Ncpu + 1` threads.

#### `Exchanger`
A two-party barrier that also swaps data at the rendezvous. Classic use: one thread fills a buffer, another drains it; they `exchange()` full-for-empty at the meeting point. The exchange is **safe publication** in both directions.

### 5.6 Building an Efficient, Scalable Result Cache — `Memoizer` Evolution

The capstone: build a caching wrapper around a slow function `Computable<A, V>`. Watch four iterations progressively fix their problems.

#### Memoizer1 — `HashMap` + `synchronized` method
```java
public synchronized V compute(A arg) {
    V result = cache.get(arg);
    if (result == null) {
        result = c.compute(arg);
        cache.put(arg, result);
    }
    return result;
}
```
Correct but **serializes all calls** to `compute`. A queue of threads wanting different values all wait behind one slow computation. Caching made things *worse* — sometimes slower than no cache.

#### Memoizer2 — Switch to `ConcurrentHashMap`
Drops the method-level `synchronized`; uses `ConcurrentHashMap` for safe concurrent access. Actually concurrent now, but: two threads can `compute` the **same** `arg` at the same time, both see no entry, both do the expensive work. Duplicate work — bad for memoization, **dangerous** for one-shot-initialization caches.

#### Memoizer3 — Cache `Future<V>` instead of `V`
Store the **in-progress computation** in the map, not just the result. If you find an existing `Future`, you wait on *it* instead of racing to start another:

```java
Future<V> f = cache.get(arg);
if (f == null) {
    FutureTask<V> ft = new FutureTask<>(() -> c.compute(arg));
    f = ft;
    cache.put(arg, ft);
    ft.run();
}
return f.get();
```

Almost perfect — but `get`+`put` is still a **non-atomic check-then-act**. Two threads can both `get` null and both `put` their own `FutureTask`; the second overwrites, but both have already started the computation.

#### Memoizer (final) — Use `putIfAbsent`
```java
FutureTask<V> ft = new FutureTask<>(eval);
f = cache.putIfAbsent(arg, ft);
if (f == null) { f = ft; ft.run(); }        // we won — run the computation
// else: someone beat us. Use their Future.
return f.get();
```

`putIfAbsent` is atomic — exactly one `FutureTask` wins. Wrapped in a `while (true)` loop plus **cache-pollution cleanup**: if `f.get()` throws `CancellationException`, remove the cancelled Future from the cache and retry. (You might similarly clean up on `RuntimeException` if retrying could succeed.)

Still not addressed: **expiration** and **eviction** (a subclass of `FutureTask` with expiration timestamps plus periodic sweep / LRU are the usual extensions).

Finally, `Factorizer` (Listing 5.20) drops `Memoizer` into the servlet from Chapter 2, delivering real caching — correct and scalable.

## "Concurrency Cheat Sheet" (Part I Summary, p. 69)

- **It's the mutable state, stupid.** All concurrency issues boil down to coordinating access to mutable state. The less mutable state, the easier thread safety is.
- **Make fields `final` unless they need to be mutable.**
- **Immutable objects are automatically thread-safe** — share freely without locking or defensive copies.
- **Encapsulation makes it practical to manage complexity** — encapsulate both *state* and *synchronization*.
- **Guard each mutable variable with a lock.**
- **Guard all variables in an invariant with the same lock.**
- **Hold locks for the duration of compound actions.**
- **A program that accesses a mutable variable from multiple threads without synchronization is a broken program.**
- **Don't rely on clever reasoning about why you don't need to synchronize.**
- **Include thread safety in the design process** — or explicitly document the class as not thread-safe.
- **Document your synchronization policy.**

## Gotchas & Pitfalls

- **Synchronized collections are thread-safe but not race-free.** Compound operations (get-last, iterate, put-if-absent) still need client-side locking on the wrapper itself.
- **Hidden iterators bite.** `toString`, `hashCode`, `equals`, `containsAll`, `removeAll`, `retainAll`, and collection copy constructors all iterate. A `println(... + set)` without the lock can throw CME. Encapsulate the collection (e.g. via `synchronizedSet`) to avoid this class of bugs.
- **CME is not a guarantee.** The modification-count check is unsynchronized — it may miss modifications. Treat it as an early-warning indicator, not a safety net.
- **Locking during iteration is fine for correctness, bad for throughput** and a deadlock risk if `doSomething(element)` is an "alien method". Often better: clone under the lock, iterate the clone.
- **`ConcurrentHashMap` cannot be locked for exclusive access.** If you *need* that (e.g. adding several mappings atomically or iterating twice in a row and seeing the same data), use `synchronizedMap` — or redesign around the concurrent-map compound ops.
- **`ConcurrentHashMap.size` and `isEmpty` are approximations** under concurrency. That's by design; these numbers are moving targets anyway.
- **`CopyOnWriteArrayList` is only good for iteration-heavy workloads.** Every mutation copies the whole backing array. Great for listeners; terrible for a write-heavy queue.
- **Unbounded queues can OOM you.** If producers outpace consumers forever, work piles up. **Prefer bounded queues** + an overload policy (reject, shed, disk-spill, throttle).
- **`SynchronousQueue` has zero capacity.** A `put` blocks until a consumer is waiting; no staging. Great for direct handoff; dangerous if consumers aren't always ready.
- **After handing an object off via a queue, don't touch it again** — that's the whole point of serial thread confinement.
- **Don't swallow `InterruptedException`.** Either propagate it, or catch and call `Thread.currentThread().interrupt()` to restore the flag. Catch-and-ignore destroys cancellation.
- **`Thread.interrupt()` is a polite request, not a command.** The target thread must cooperate by checking its interrupt flag or being in an interruptible blocking call. Long CPU-bound loops won't notice.
- **Don't start a thread from a constructor** (echoing Chapter 3). `Preloader` exposes `start()` for this reason.
- **`Future.get()` wraps everything in `ExecutionException`**, including `RuntimeException` and `Error`. Use `launderThrowable` to unwrap cleanly; handle known checked exceptions before calling it.
- **Semaphore permits aren't tied to threads.** One thread can release a permit another acquired — useful, but easy to misuse. Also, you can release more than you initialize with.
- **Latches are one-shot; barriers are reusable.** Pick by reuse requirement.
- **Memoization with `get`+`put` is a check-then-act race** (Memoizer3). Always use `putIfAbsent` on a `ConcurrentMap` for atomic insertion.
- **Cache `Future<V>` instead of `V` when computations are expensive** — prevents duplicate work and lets late arrivers wait on the in-progress task.
- **Cache pollution**: if a cached `Future` completes exceptionally or is cancelled, every future lookup inherits that failure. Remove failed/cancelled Futures from the cache (Memoizer final loop).

## Page References
- Synchronized collections, `getLast`/`deleteLast` race: pp. 79-82
- Iterators and `ConcurrentModificationException`: pp. 82-83
- Hidden iterators, `HiddenIterator`: p. 84
- Concurrent collections overview, `ConcurrentHashMap`: pp. 84-86
- `ConcurrentMap` compound operations, `CopyOnWriteArrayList`: pp. 86-87
- Blocking queues, producer-consumer pattern, dish-rack analogy: pp. 87-90
- `BlockingQueue` implementations, `SynchronousQueue`: p. 89
- Desktop search (`FileCrawler` / `Indexer`): pp. 89-91
- Serial thread confinement, object pools: p. 90
- Deques, work stealing: p. 92
- Blocking / interruptible methods, handling `InterruptedException`: pp. 92-94
- Synchronizers overview: pp. 94
- `CountDownLatch`, `TestHarness` starting/ending gates: pp. 94-95
- `FutureTask`, `Preloader`, `launderThrowable`: pp. 95-98
- Semaphores, `BoundedHashSet`: pp. 98-99
- `CyclicBarrier`, `CellularAutomata`, `Exchanger`: pp. 99-101
- `Memoizer1` → `Memoizer` evolution, `Factorizer`: pp. 101-105
- "Concurrency cheat sheet" (Part I summary): p. 69 (Part I summary at end of ch. 5)
