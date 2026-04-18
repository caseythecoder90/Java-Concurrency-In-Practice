# Chapter 4: Composing Objects

## Key Concepts

Chapter 3 covered the low-level mechanics of sharing objects across threads. Chapter 4 zooms out and asks: how do we **structure** classes so thread safety composes? The goal is to build thread-safe programs out of thread-safe parts without having to re-analyze memory at every call site.

### 4.1 Designing a Thread-safe Class

A thread-safe class design starts from **encapsulation**. Putting state behind an object's methods makes it possible to verify safety by looking at one class rather than the entire program.

The book's three-step design recipe:

1. **Identify the variables that form the object's state.**
2. **Identify the invariants that constrain the state variables.**
3. **Establish a policy for managing concurrent access to the object's state** — the *synchronization policy*.

An object's state includes its fields plus the state of any objects its fields reach (its object graph — e.g. a `LinkedList`'s state includes all its nodes). The **synchronization policy** specifies the combination of immutability, thread confinement, and locking used to maintain thread safety, and which locks guard which variables. **Document it.**

`Counter` (Listing 4.1) is a minimal example using the **Java monitor pattern**: one `@GuardedBy("this") long value` field, two `synchronized` methods. Its state space is `0..Long.MAX_VALUE`, with the invariant `value >= 0` and the post-condition that the next state is `current + 1`.

#### Gathering Synchronization Requirements
- **State space**: the range of values a variable or object can legitimately take. Smaller is easier to reason about — `final` fields shrink the state space; immutability collapses it to one state.
- **Invariants**: constraints that identify some states as invalid (e.g. `Counter.value` must be non-negative).
- **Post-conditions**: constraints on which state transitions are valid (e.g. `17 -> 18`, not `17 -> 42`).
- **Multivariable invariants** (like `lower <= upper` in a range class) create **atomicity requirements**: related variables must be fetched/updated while holding the same lock for the entire operation.

> **"You cannot ensure thread safety without understanding an object's invariants and post-conditions. Constraints on the valid values or state transitions for state variables can create atomicity and encapsulation requirements."** (p. 56)

#### State-Dependent Operations
Some operations have **state-based preconditions** ("you can't remove from an empty queue"). In a single-threaded program, if the precondition fails, the op fails. In a concurrent program, you have a new option: **wait until the precondition becomes true** thanks to another thread's action. Built-in `wait`/`notify` exist for this but are tricky; Chapter 5 covers higher-level primitives (`BlockingQueue`, `Semaphore`, etc.), and Chapter 14 covers building your own.

#### State Ownership
**Ownership** isn't a language construct — it's a design element. The **owner** of a state variable decides the locking protocol for it. Rules of thumb:
- **Encapsulation ≈ ownership**: an object typically owns what it encapsulates.
- Objects **passed in** to methods or constructors usually aren't owned (exceptions: ownership-transferring factories like `Collections.synchronizedXxx`).
- **Split ownership** is common. Collections own their internal structure; clients own the elements. `ServletContext` owns its map infrastructure but the *attributes* placed in it are application-owned — the application must ensure those are thread-safe / effectively immutable / guarded, since the container can't know the locking protocol.

### 4.2 Instance Confinement

If an object isn't thread-safe, you can still use it safely by **instance confinement**: encapsulate it inside a thread-safe wrapper that takes responsibility for locking all access. Known code paths → analyzable safety.

> **"Encapsulating data within an object confines access to the data to the object's methods, making it easier to ensure that the data is always accessed with the appropriate lock held."** (p. 59)

`PersonSet` (Listing 4.2) wraps a non-thread-safe `HashSet`. The set is `private final`, never exposed, and every access happens through a `synchronized` method. Result: thread-safe, even though the inner `HashSet` isn't.

This is how `Collections.synchronizedList` and friends work — the **Decorator pattern** wraps a non-thread-safe collection with one whose methods all `synchronize` on the wrapper. As long as the original reference never escapes (the wrapper holds the only reference to the underlying collection), the result is thread-safe.

Instance confinement also allows flexibility: different state variables can be guarded by different locks.

#### The Java Monitor Pattern
Taking instance confinement to its logical conclusion: an object whose **mutable state is entirely private**, entirely guarded by the object's **own intrinsic lock**. All synchronization lives in one place, which is easy to reason about. Used by `Vector`, `Hashtable`, `Counter`, and many other classes.

It's just a convention — any consistently used lock works. `PrivateLock` (Listing 4.3) uses a **private `Object` lock** instead of `this`:

```java
private final Object myLock = new Object();
@GuardedBy("myLock") Widget widget;
void someMethod() {
    synchronized (myLock) { /* access widget */ }
}
```

**Why prefer a private lock?**
- Client code **cannot acquire** it, so clients cannot accidentally participate in (or break) the synchronization policy.
- Verifying the locking policy requires only examining one class, not the entire program.
- A publicly accessible lock lets hostile or buggy clients cause liveness issues (deadlocks, starvation).

#### Example: `MonitorVehicleTracker`
A vehicle tracker where a view thread reads `Map<String, Point>` locations and updater threads push GPS updates. Using the Java monitor pattern with mutable points (Listings 4.4, 4.5):
- The `Map<String, MutablePoint>` is `@GuardedBy("this")` and never exposed.
- `getLocations` returns a **deep copy** (because `MutablePoint` is mutable and unsafe to share).
- Readers see a consistent **snapshot** — the returned map doesn't update as vehicles move.

Trade-offs: copying on every call can be expensive if the fleet is large, and the snapshot goes stale. The tracker's intrinsic lock is held for the duration of `deepCopy`, which is a long-running operation under the lock — a liveness concern at scale.

### 4.3 Delegating Thread Safety

When components of a composite are already thread-safe, do you need another layer? **It depends** on whether the class imposes invariants that couple the component state variables.

**Delegation** means a class lets its thread-safe components handle synchronization and adds nothing itself. `CountingFactorizer` from Chapter 2 delegates to `AtomicLong`; it works because the counter is independent of any other state and imposes no constraints of its own.

#### Example: `DelegatingVehicleTracker` (Listing 4.7)
- Uses an **immutable `Point`** (Listing 4.6).
- Uses `ConcurrentHashMap` for the map.
- No explicit synchronization in the tracker — all state is delegated.
- `getLocations` returns a `Collections.unmodifiableMap(locations)` — an unmodifiable **live view**. Callers see updates as they happen, which can be a benefit (freshness) or liability (inconsistent view) depending on requirements.
- If you want a static snapshot instead, return `Collections.unmodifiableMap(new HashMap<>(locations))` (Listing 4.8). Only the map structure is copied — the `Point`s are immutable.

#### Independent State Variables
Delegation can target **multiple** underlying thread-safe variables, **as long as those variables are independent** — the composite imposes no invariants across them. `VisualComponent` (Listing 4.9) has two `CopyOnWriteArrayList`s for mouse and key listeners. Since the two lists don't interact, the class can delegate safety to both.

#### When Delegation Fails — `NumberRange` (Listing 4.10)
`NumberRange` uses two `AtomicInteger`s but imposes `lower <= upper`. Its `setLower` does a check-then-act — `if (i > upper.get()) throw; lower.set(i);`. If `setLower(5)` and `setUpper(4)` run concurrently on the range `(0, 10)`, both checks pass and both writes apply, leaving `(5, 4)` — an **invalid state**.

> **"If a class is composed of multiple independent thread-safe state variables and has no operations that have any invalid state transitions, then it can delegate thread safety to the underlying state variables."** (p. 68)

This is structurally the same rule as for `volatile`: a variable is suitable for `volatile` only if it doesn't participate in invariants with other state variables. If there *are* cross-variable invariants or invalid transitions, the composite must add its own locking.

#### Publishing Underlying State Variables
A state variable can be safely **published** (made accessible to clients to read/modify directly) only if:
1. It is thread-safe.
2. It doesn't participate in invariants that constrain its value.
3. It has no prohibited state transitions.

`Counter.value` **cannot** be safely published (clients could set it negative). `VisualComponent.mouseListeners` **can** (no cross-variable invariants). Publishing is still a design commitment — it constrains future evolution.

#### Example: `PublishingVehicleTracker` (Listings 4.11, 4.12)
Uses `SafePoint` — a **thread-safe mutable point** with a combined `get()` returning `int[]{x, y}` so the two coordinates are read atomically. (Separate `getX`/`getY` would let callers observe a point the vehicle was never at.) The tracker publishes the underlying `SafePoint`s via an unmodifiable map. This is thread-safe **only** because the tracker imposes no constraints on valid locations — no "vetos," no cross-vehicle invariants.

#### `SafePoint` — The Private Constructor Capture Idiom (a closer look)

This is one of the most instructive patterns in the book, so it's worth unpacking slowly. Here's the class:

```java
@ThreadSafe
public class SafePoint {
    @GuardedBy("this") private int x, y;

    private SafePoint(int[] a) { this(a[0], a[1]); }   // ← the private constructor
    public  SafePoint(SafePoint p) { this(p.get()); }  // ← copy constructor
    public  SafePoint(int x, int y) { this.x = x; this.y = y; }

    public synchronized int[] get()          { return new int[]{ x, y }; }
    public synchronized void  set(int x, int y) { this.x = x; this.y = y; }
}
```

##### The question: why not just `this(p.x, p.y)` in the copy constructor?

It looks fine — read `p.x`, read `p.y`, pass them in. But there's a race:

- Thread A calls `new SafePoint(p)` and reads `p.x` → gets `3`.
- Thread B calls `p.set(10, 20)` — atomically updates *both* coordinates.
- Thread A reads `p.y` → gets `20`.
- The copy is `(3, 20)` — a position the point **never held**.

Same compound-atomicity hazard as everywhere else in the book: two individually safe reads don't compose into one atomic read of both fields.

(Even if `x` and `y` had individually synchronized getters, that wouldn't help — the *gap between two separate calls* is what leaks the inconsistency.)

##### What the idiom actually does

The copy constructor delegates to `p.get()`, which is `synchronized` and returns **both coordinates captured atomically** in a single array:

```java
public SafePoint(SafePoint p) { this(p.get()); }
// p.get() returns new int[]{x, y} under p's lock — atomic snapshot
```

Now you have an `int[]` — not two separate `int`s — and the primary constructor takes two `int`s. You need a bridge. Making that bridge *public* would expose an awkward `SafePoint(int[])` API to callers, so it's **private**:

```java
private SafePoint(int[] a) { this(a[0], a[1]); }
```

By the time this constructor runs, the array is a **local, already-atomic snapshot**. Splitting `a[0]` and `a[1]` here is safe because the array is not shared mutable state.

##### The call chain

```
new SafePoint(p)
  → this(p.get())           // atomically captures {x, y} under p's lock
    → private SafePoint(int[] a)
      → this(a[0], a[1])    // unpacking a LOCAL array — no race possible
        → SafePoint(int x, int y)   // sets fields
```

The private constructor is pure **plumbing**: it bridges the atomic `get()` (which returns `int[]`) to the primary constructor (which takes two `int`s). Making it private keeps that bridge out of the public API.

##### The broader principle

When you need to atomically capture multiple pieces of state during construction:
1. Grab the snapshot in a single synchronized method call (here `p.get()`).
2. Stash the result in a local container (the array).
3. Pass that container to a **private constructor** that unpacks it.

The private constructor "captures" the already-atomic snapshot — hence **private constructor capture idiom** (Bloch and Gafter). You'll see variants of it anywhere you need to atomically grab state during construction without exposing weird intermediate constructors as part of the public API.

### 4.4 Adding Functionality to Existing Thread-safe Classes

Suppose you need a thread-safe list with an atomic **put-if-absent**. Your options, ranked from best to worst:

#### 1. Modify the Original Class
Safest — the synchronization policy stays in one source file. Often impossible (no source access, not allowed to modify).

#### 2. Extend the Class — `BetterVector` (Listing 4.13)
```java
public class BetterVector<E> extends Vector<E> {
    public synchronized boolean putIfAbsent(E x) {
        boolean absent = !contains(x);
        if (absent) add(x);
        return absent;
    }
}
```
Works because `Vector`'s synchronization policy is to `synchronize(this)` on every method. The subclass's `synchronized` method acquires the same lock. **Fragile** though: the locking policy now spans multiple source files, and if the base class ever changes which lock it uses, the subclass silently breaks.

#### 3. Client-Side Locking — `ListHelper` (Listings 4.14 & 4.15)
When you can't modify or extend (e.g. a `Collections.synchronizedList` whose class you don't even know), you can lock **on the same lock the object uses**.

Listing 4.14 gets this wrong — `putIfAbsent` is `synchronized`, but on the *helper*, not the list. The list's internal `synchronized` methods lock on the wrapper collection; calls from the helper interleave freely.

Listing 4.15 fixes it by synchronizing on the **list** itself:
```java
public boolean putIfAbsent(E x) {
    synchronized (list) {
        boolean absent = !list.contains(x);
        if (absent) list.add(x);
        return absent;
    }
}
```
This works only because `Vector` and the synchronized-wrapper collections **document** that client-side locking uses the wrapper's intrinsic lock. Outside classes that promise this, you're guessing.

> **"Just as extension violates encapsulation of implementation, client-side locking violates encapsulation of synchronization policy."** (p. 73)

#### 4. Composition — `ImprovedList` (Listing 4.16) — **The Preferred Approach**
Wrap the underlying `List` in a new class that implements `List` itself and uses **its own intrinsic lock** to guard all delegating methods:

```java
public class ImprovedList<T> implements List<T> {
    private final List<T> list;
    public ImprovedList(List<T> list) { this.list = list; }
    public synchronized boolean putIfAbsent(T x) { /* ... */ }
    public synchronized void clear() { list.clear(); }
    // ... delegate every other List method synchronized on this
}
```

Why composition wins:
- Locking lives entirely in `ImprovedList` — no coupling to the underlying class's locking strategy.
- Works whether the inner list is thread-safe or not.
- Effectively applies the **Java monitor pattern** over the existing list.

Assumption: once a list is passed to `ImprovedList`, **the client must not access the underlying list directly again**.

### 4.5 Documenting Synchronization Policies

> **"Document a class's thread safety guarantees for its clients; document its synchronization policy for its maintainers."** (p. 74)

Clients need to know: *Is this class thread-safe? Does it make callbacks with a lock held? Which lock do I acquire for client-side locking?* Maintainers need to know: *which fields are `@GuardedBy` which lock, which invariants hold, which operations must stay atomic.*

Practical rules of thumb:
- Use the `@GuardedBy` annotation on fields. It's cheap and enormously helpful.
- Even classes not intended to be thread-safe should **say so** — non-commitment is expensive for users.
- If you don't want to support client-side locking, say so explicitly.

#### Interpreting Vague Documentation
Many specs (JDBC, Servlet) don't talk about concurrency at all. When docs are silent, **reason from the implementer's perspective**:
- **`ServletContext`, `HttpSession`, `DataSource`** → accessed concurrently by the container; thread-safe is the only reasonable implementation. No `getConnection()` example ever shows client-side locking.
- **Attributes stored via `setAttribute`** → owned by the application. The container can't know your locking protocol and may serialize session attributes for replication. **Make them thread-safe or effectively immutable.**
- **JDBC `Connection` objects** → *not* inherently shared. If an activity using one spans threads, the activity is responsible for guarding it (which is why pools typically confine a connection to one thread for the duration of a request).
- **`SimpleDateFormat`** is **not thread-safe** despite looking like it should be — a classic gotcha the Javadoc didn't mention until JDK 1.4.

## Gotchas & Pitfalls
- **Delegation is not always enough.** If the composite imposes any invariant spanning multiple state variables, the underlying variables being individually thread-safe is irrelevant — you still need a lock that covers the whole compound action. See `NumberRange`.
- **Split ownership is easy to get wrong.** `ServletContext.setAttribute` looks simple, but the values are application-owned. The container can serialize them behind your back; make them thread-safe or effectively immutable.
- **Private lock > intrinsic lock.** A public intrinsic lock invites clients (or subclasses) to participate in your synchronization policy and turns local correctness into a whole-program concern.
- **Extension is fragile.** A subclass's synchronized methods silently break if the base class ever changes its locking policy. Works for `Vector` only because its policy is frozen in the spec.
- **Client-side locking is even more fragile than extension** — the locking code lives in a class that has no relationship to the one it's locking. Only use it when the class **documents** it as supported.
- **Separate `getX`/`getY` on a "thread-safe" point are a trap.** Without a combined accessor, callers observe values the object never held. See `SafePoint.get()`.
- **"Live" unmodifiable maps aren't snapshots.** `Collections.unmodifiableMap(concurrentMap)` gives you a read-only view that still updates as the backing map changes. Pick the semantics deliberately.
- **Copy-on-call under a lock is a liveness risk.** `MonitorVehicleTracker.getLocations` holds the tracker's lock for the whole `deepCopy`. Fine for 10 vehicles, not fine for 10,000.
- **`SimpleDateFormat` is not thread-safe.** Don't share instances across threads; use `ThreadLocal<SimpleDateFormat>` or `DateTimeFormatter`.
- **Public lock = deadlock hazard.** Clients who lock on your object in the wrong order can deadlock your code through no fault of your own.
- **Document synchronization at design time.** Weeks later the details are a blur — write `@GuardedBy` annotations and a short policy comment while you still remember.

## Page References
- Three-step design process: pp. 55
- State, invariants, post-conditions, state space: pp. 56
- State-dependent operations: p. 57
- State ownership, split ownership, `ServletContext`: pp. 57-58
- Instance confinement, `PersonSet`: pp. 59-60
- Synchronized collection wrappers / Decorator pattern: p. 60
- Java monitor pattern, `Counter`, `PrivateLock`: pp. 60-61
- `MonitorVehicleTracker`, `MutablePoint`: pp. 61-63
- Delegation, `DelegatingVehicleTracker`, immutable `Point`: pp. 64-66
- Independent state variables, `VisualComponent`: pp. 66-67
- `NumberRange`, when delegation fails: pp. 67-68
- Publishing underlying state variables: pp. 68
- `PublishingVehicleTracker`, `SafePoint`, private constructor capture idiom: pp. 69-70
- Adding functionality: modify, extend, client-side locking, composition: pp. 71-74
- `BetterVector`, `ListHelper`, `ImprovedList`: pp. 71-74
- Documenting synchronization policies: pp. 74-77
- Interpreting vague documentation (`SimpleDateFormat`, `DataSource`): pp. 76-77
