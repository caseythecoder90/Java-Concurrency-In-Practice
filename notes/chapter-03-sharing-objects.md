# Chapter 3: Sharing Objects

## Key Concepts

Chapter 2 was about using synchronization to prevent multiple threads from touching the same data simultaneously. Chapter 3 is about the *other* side of `synchronized` — **memory visibility** — and the techniques for sharing and publishing objects so they can be accessed safely by multiple threads.

> **"Synchronization also has another significant, and subtle, aspect: memory visibility. We want not only to prevent one thread from modifying the state of an object when another is using it, but also to ensure that when a thread modifies the state of an object, other threads can actually see the changes that were made."** (p. 33)

### 3.1 Visibility

In a single-threaded program, writing a value and reading it back gives you that value. Across threads, this is **not guaranteed**. Without synchronization, there is no guarantee that a reading thread will ever see a value written by another thread — or see it on any timely basis.

The book's `NoVisibility` example (Listing 3.1) shows two threads sharing `ready` and `number` fields without synchronization. The reader thread spins on `while (!ready)`. It may print `42`, print `0`, or **loop forever** — because:

1. The write to `ready` may never become visible to the reader.
2. Even if it does, **reordering** is allowed. The main thread writes `number = 42; ready = true;`, but the reader may observe the write to `ready` *before* the write to `number`.

> **"In the absence of synchronization, the compiler, processor, and runtime can do some downright weird things to the order in which operations appear to execute."** (p. 34)

#### Stale Data
Without synchronization, readers can see **stale values** — values that were valid at some earlier moment but have since been updated by another thread. Worse, staleness is **not all-or-nothing**: a thread can see an up-to-date value for one field and a stale value for another field that was written before it. `MutableInteger` (Listing 3.2) is unsafe because `get` and `set` share the `value` field without synchronization; synchronizing *only* the setter is not enough, because readers would still see stale values. `SynchronizedInteger` (Listing 3.3) fixes this by synchronizing both.

#### Nonatomic 64-bit Operations
The JVM guarantees that reads and writes of most variables are atomic (you see values that *some* thread actually wrote — "out-of-thin-air safety"). The exception is **`long` and `double` that are not declared `volatile`**: the JVM is allowed to treat a 64-bit access as two 32-bit operations. A reader can see the high 32 bits of one write paired with the low 32 bits of another — a value no thread ever wrote.

> **"It is not safe to use shared mutable long and double variables in multithreaded programs unless they are declared volatile or guarded by a lock."** (p. 36)

#### Locking and Visibility
Intrinsic locks give visibility guarantees, not just mutual exclusion. When thread A releases a lock and thread B later acquires the **same** lock, **everything A did before releasing the lock is visible to B after acquiring it**.

> **"Locking is not just about mutual exclusion; it is also about memory visibility. To ensure that all threads see the most up-to-date values of shared mutable variables, the reading and writing threads must synchronize on a common lock."** (p. 37)

This is the *other* reason the rule from Chapter 2 exists — all threads accessing a shared variable must synchronize on the same lock, both for mutual exclusion *and* for visibility.

#### Volatile Variables
`volatile` is a **weaker form of synchronization** that guarantees visibility but **not atomicity**. A volatile variable is never cached in registers or in CPU caches in a way that hides it from other processors; a read always returns the most recent write by any thread. The compiler and runtime are also forbidden from reordering operations on it with other memory operations.

Writing a volatile variable has the memory-visibility effect of exiting a `synchronized` block; reading one has the effect of entering one — everything visible to the writing thread before the volatile write is visible to a reading thread after the volatile read.

**Use `volatile` only when:**
- Writes don't depend on the current value (or only one thread writes), **and**
- The variable doesn't participate in invariants with other state variables, **and**
- Locking isn't required for some other reason.

> **"Locking can guarantee both visibility and atomicity; volatile variables can only guarantee visibility."** (p. 38)

The canonical use is a completion/shutdown/status flag, such as `volatile boolean asleep` (Listing 3.4). `count++` on a volatile is **not** atomic — `volatile` is not a replacement for `AtomicLong`.

### 3.2 Publication and Escape

**Publishing** an object means making it available outside its current scope — storing a reference in a static field, returning it from a non-private method, or passing it to another class. An object that is published when it shouldn't have been has **escaped**.

Ways to escape:
- **Public static field** (Listing 3.5): `knownSecrets = new HashSet<>()` makes the set visible to any class or thread.
- **Returning a reference to internal state** (Listing 3.6): `getStates()` returns the private array directly — callers can mutate it. What was supposed to be private has been effectively made public.
- **Publishing inner-class instances**: an inner class holds a hidden reference to its enclosing instance, so publishing the inner class publishes the outer one.
- **Passing `this` to alien methods**: any method whose behavior isn't fully specified by your class (including overridable methods you declare) may retain or publish the reference.

> **"Publishing an object also publishes any objects referred to by its non-private fields. More generally, any object that is reachable from a published object by following some chain of non-private field references and method calls has also been published."** (p. 40)

#### Safe Construction Practices — Don't Let `this` Escape
`ThisEscape` (Listing 3.7) registers an anonymous `EventListener` from its constructor. The listener is an inner class, so it carries an implicit reference to the outer `ThisEscape`. Once the listener is registered, other threads can reach `ThisEscape` **before its constructor returns** — seeing a partially constructed object.

> **"Do not allow the `this` reference to escape during construction."** (p. 41)

Common ways `this` accidentally escapes from a constructor:
- **Starting a thread** from the constructor (the new thread shares `this`).
- Registering listeners, callbacks, or observers.
- Calling overridable (non-final, non-private) instance methods.

The fix (Listing 3.8, `SafeListener`) is the **private-constructor + public factory method** idiom: do construction in a private constructor, register with external systems only *after* the constructor returns, from the static factory.

### 3.3 Thread Confinement

If data is only accessed from a single thread, no synchronization is needed. **Thread confinement** is one of the simplest ways to achieve thread safety — even if the object itself is not thread-safe, confining it to one thread makes its use thread-safe.

Examples from the wild:
- **Swing**: visual components and data models are confined to the event dispatch thread. Accessing them from another thread is a bug. `SwingUtilities.invokeLater` exists so other threads can schedule work onto the EDT.
- **Pooled JDBC `Connection`s**: `Connection` isn't required to be thread-safe. Pools hand a connection to a thread for the duration of a request and don't give it to another thread until it's returned.

The language has no keyword for "confined to a thread" — it's a **design discipline**, enforced by convention and documentation. Three flavors:

#### Ad-Hoc Thread Confinement
Responsibility for confinement lies entirely with the implementation. Fragile — nothing in the language helps you. Use sparingly; prefer stack confinement or `ThreadLocal` when possible.

A special case: read-modify-write on a `volatile` variable is safe **if only one thread ever writes it** (multiple readers are fine) — you're confining writes to one thread and relying on volatile for visibility.

#### Stack Confinement
Objects reachable only through local variables are confined to the executing thread — the stack isn't accessible to other threads. Local primitives cannot escape (you can't get a reference to a primitive). Local object references require discipline: as long as you don't let them leak, the referent is stack-confined. `loadTheArk` (Listing 3.9) uses a local `TreeSet`; as long as no reference to it leaks, it's thread-confined even though `TreeSet` isn't thread-safe.

> **"Using a non-thread-safe object in a within-thread context is still thread-safe."** (p. 43)

#### ThreadLocal
`ThreadLocal<T>` gives each thread its own copy of a value. `get`/`set` access the *current* thread's copy. Conceptually like a `Map<Thread, T>`, though actually stored on each `Thread` object.

Typical uses:
- **Per-thread JDBC connection** (Listing 3.10): each thread's `ThreadLocal<Connection>` lazily initializes to its own pooled connection.
- **Per-thread scratch buffers** to avoid locking or reallocation.
- **Framework context**: J2EE containers use a static `ThreadLocal` to track the currently executing transaction, avoiding passing it through every method call.

Caveat: it's tempting to use `ThreadLocal` as a global variable or "hidden parameter," which introduces hidden coupling between classes and hurts reusability. Use with care.

### 3.4 Immutability

Immutable objects are the other end-run around synchronization. If state cannot change, there are no stale values, lost updates, or inconsistent intermediate states to worry about.

> **"Immutable objects are always thread-safe."** (p. 46)

An object is immutable if:
1. **Its state cannot be modified after construction.**
2. **All its fields are `final`.**
3. **It is properly constructed** (the `this` reference does not escape during construction).

Note: `final` fields alone don't imply immutability — a `final` field can reference a mutable object. And an immutable object can still *use* mutable objects internally, as long as it never exposes them or allows modification after construction. `ThreeStooges` (Listing 3.11) uses a mutable `HashSet` internally but never exposes it or modifies it after construction, so the object is immutable.

> **"Program state stored in immutable objects can still be updated by 'replacing' immutable objects with a new instance holding new state."** (p. 47)

#### Final Fields
`final` fields cannot be reassigned, and also have **special Java Memory Model semantics**: the JMM guarantees that a properly constructed object's `final` fields are visible to other threads without synchronization (initialization safety — Section 3.5.2). This is what makes immutable objects safe to share freely.

Rule of thumb: just as fields should be `private` unless they need more visibility, they should be `final` unless they need to be mutable.

#### Using Volatile to Publish Immutable Objects — `OneValueCache` / `VolatileCachedFactorizer`
The failed `UnsafeCachingFactorizer` from Chapter 2 had two related fields (last number, last factors) that couldn't be updated atomically with two `AtomicReference`s. The fix (Listings 3.12 and 3.13) is beautiful:

- `OneValueCache` is an **immutable holder** containing *both* the number and its factors. Defensive array copies in the constructor and getter preserve immutability.
- `VolatileCachedFactorizer` has a single `volatile OneValueCache cache` field. On a cache hit, it reads `cache` once and calls `getFactors`. On a miss, it computes new factors and assigns a **new** `OneValueCache` to the volatile field.

Since the holder is immutable, any thread that sees a reference to it sees a complete, consistent snapshot. Since the reference field is volatile, assigning a new holder is instantly visible to other threads. **No explicit locking.**

> **"Race conditions in accessing or updating multiple related variables can be eliminated by using an immutable object to hold all the variables."** (p. 49)

### 3.5 Safe Publication

Simply storing a reference into a public field is **not enough** to publish it safely:

```java
// Unsafe publication (Listing 3.14)
public Holder holder;
public void initialize() { holder = new Holder(42); }
```

Because of visibility issues, another thread might:
- See a stale reference (null, or the default value of the field) even after `initialize` has run.
- See the reference but see the `Holder`'s fields in **inconsistent, partially constructed state**.

`Holder.assertSanity` (Listing 3.15) reads `n` twice and throws if the two reads differ. Under improper publication, **this can actually throw** — a thread can see the default value of `n` on the first read and the constructed value on the second, even though no one modified `n` after construction.

> **"You cannot rely on the integrity of partially constructed objects. An observing thread could see the object in an inconsistent state, and then later see its state suddenly change, even though it has not been modified since publication."** (p. 50)

#### Immutable Objects and Initialization Safety
The JMM gives immutable objects a special guarantee: **they can be safely accessed even when synchronization was not used to publish them**. The catch — the object must truly meet the immutability criteria (unmodifiable state, all fields `final`, proper construction). Mere `final` fields in an otherwise mutable class don't get the full pass, but reads of `final` fields themselves are always safe without additional synchronization.

#### Safe Publication Idioms
To safely publish a properly constructed object, use any of:
1. **Static initializer**: `public static Holder holder = new Holder(42);` — the JVM's class-initialization synchronization handles it.
2. **Store into a `volatile` field or `AtomicReference`.**
3. **Store into a `final` field of a properly constructed object.**
4. **Store into a field guarded by a lock** (and read it with that same lock held).

Thread-safe collections implicitly provide safe publication: putting an object into a `Hashtable`, `synchronizedMap`, `ConcurrentMap`, `Vector`, `CopyOnWriteArrayList`/`Set`, `synchronizedList`/`Set`, `BlockingQueue`, or `ConcurrentLinkedQueue` safely publishes it to any thread that retrieves it.

#### Effectively Immutable Objects
Objects that aren't technically immutable but **aren't modified after publication** are **effectively immutable**. Safe publication + no further mutation = safe to share without synchronization. Classic example: putting `Date` values in a `synchronizedMap` — `Date` is mutable, but if you treat it as immutable, the map's internal synchronization is enough.

#### Mutable Objects
If an object may change after publication, safe publication only guarantees the as-published state is visible. Subsequent mutations need synchronization too:

> **"To share mutable objects safely, they must be safely published and be either thread-safe or guarded by a lock."** (p. 53)

#### Sharing Objects Safely — The Four Policies
Whenever you get a reference to an object, you need to know the "rules of engagement" — and documentation should make them explicit. The four useful policies:

| Policy | Access rules |
|---|---|
| **Thread-confined** | Owned by one thread; only that thread reads or writes. |
| **Shared read-only** | Multiple threads read concurrently; no one modifies. Includes immutable and effectively immutable objects. |
| **Shared thread-safe** | The object synchronizes internally; all threads can use its public API freely. |
| **Guarded** | Accessible only with a specific lock held. |

## Gotchas & Pitfalls
- **`NoVisibility`** can print `0` or loop forever — without synchronization, writes may never be seen, and reordering can make later writes appear before earlier ones.
- **Stale reads are not all-or-nothing.** A thread can see a fresh value for one field and a stale value for another written before it. Don't reason about "consistency" without synchronization.
- **Non-volatile `long`/`double` can tear** — a reader may observe 32 bits from one write glued to 32 bits of another. Declare them `volatile` or guard with a lock if shared.
- **`volatile` ≠ atomic.** `count++` on a volatile is still three operations; use `AtomicLong` for read-modify-write.
- **`volatile` doesn't help with invariants across multiple fields.** If fields must be updated together, use a lock or an immutable holder + volatile reference.
- **`this` escapes from constructors silently.** Starting a thread, registering a listener, or calling an overridable method from a constructor all leak a partially constructed object. Use the private-constructor + public factory-method pattern.
- **Inner classes carry an implicit outer-`this` reference.** Publishing the inner class publishes the outer one.
- **`final` fields on otherwise-mutable classes aren't enough** for the JMM's initialization-safety guarantee — only fully immutable objects get the full pass.
- **Unsafe publication can show partially constructed state**, even for fields the constructor set. The "first read returns the default value, second read returns the constructed value" hazard is real and breaks `if (x != x)`-style sanity checks.
- **Effectively immutable ≠ immutable.** It relies on the discipline that no one mutates after publication. Document it.
- **`ThreadLocal` is easy to abuse** as a hidden parameter — it creates invisible coupling between unrelated code. Prefer passing values explicitly when you can.

## Page References
- Visibility, `NoVisibility`, reordering: pp. 33-34
- Stale data, `MutableInteger` / `SynchronizedInteger`: pp. 35-36
- Nonatomic 64-bit operations (long/double tearing): pp. 36
- Locking and visibility: p. 37
- Volatile variables, `volatile boolean asleep`: pp. 37-38
- Publication and escape, inner classes, alien methods: pp. 39-41
- `ThisEscape` / `SafeListener` and safe construction: pp. 41-42
- Thread confinement (ad-hoc, stack, ThreadLocal): pp. 42-46
- Immutability, `ThreeStooges`, final fields: pp. 46-48
- `OneValueCache` / `VolatileCachedFactorizer`: pp. 48-49
- Safe publication, improper publication, `Holder.assertSanity`: pp. 49-51
- Initialization safety for immutable objects: pp. 51
- Safe publication idioms, thread-safe collections: pp. 52
- Effectively immutable and mutable object rules: pp. 53
- Four policies for sharing objects: pp. 54
