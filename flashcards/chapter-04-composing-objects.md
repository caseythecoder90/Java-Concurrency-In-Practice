# Chapter 4: Composing Objects — Flashcards

Q: What are the three steps in the book's design process for a thread-safe class?
A: 1) **Identify the state variables** that form the object's state. 2) **Identify the invariants** that constrain them. 3) **Establish a synchronization policy** — which locks guard which variables, and what combination of immutability/confinement/locking maintains thread safety. Document the policy.

Q: What is a "synchronization policy"?
A: The design rules for how an object coordinates access to its state: which fields are guarded by which locks, which are immutable, which are thread-confined, and which operations must be atomic. It belongs in comments and `@GuardedBy` annotations, not just in your head.

Q: How do invariants and post-conditions create synchronization requirements?
A: **Invariants** (valid states) force you to encapsulate the state so clients can't put it in an invalid state. **Post-conditions** (valid state transitions) force compound actions to be atomic — you can't release the lock between steps, or another thread may observe an illegal intermediate state.

Q: What is the Java monitor pattern?
A: An object encapsulates **all its mutable state** and guards it with the object's **own intrinsic lock**. All access goes through `synchronized` methods. Used by `Vector`, `Hashtable`, `Counter`. The primary advantage is simplicity — synchronization logic lives in one place.

Q: Why is using a private `Object` lock often better than using `this` as the intrinsic lock?
A: A private lock is **encapsulated** — client code cannot acquire it, so clients can't accidentally participate in (or break) your synchronization policy or cause liveness problems. Verifying the locking policy requires examining only one class instead of the whole program.

Q: What is instance confinement?
A: Encapsulating a non-thread-safe object entirely within a thread-safe class, where the inner object never escapes and all access goes through the wrapper's synchronized methods. `PersonSet`'s private `HashSet` is instance-confined. This is how `Collections.synchronizedList` works (Decorator pattern).

Q: What is "state ownership" and why does it matter for thread safety?
A: Ownership isn't a language construct — it's a design concept. The **owner** of a state variable decides its locking protocol. Encapsulation typically implies ownership. "Split ownership" (like `ServletContext` attributes) is common: the container owns the map, the application owns the values, and each is responsible for its side's safety.

Q: What is delegation of thread safety?
A: The composite class relies on **thread-safe underlying components** to provide its thread safety, and adds no synchronization of its own. `CountingFactorizer` delegates to `AtomicLong`. `DelegatingVehicleTracker` delegates to `ConcurrentHashMap` + immutable `Point`.

Q: What's the rule for when a composite can safely delegate to multiple thread-safe components?
A: The underlying components must be **independent** — the composite class must impose **no invariants** involving multiple state variables, and **no operations with invalid state transitions**. `VisualComponent` (two listener lists, no relationship) qualifies. `NumberRange` (lower ≤ upper) does not.

Q: Why isn't `NumberRange` (Listing 4.10) thread-safe even though it uses `AtomicInteger`s?
A: `setLower` and `setUpper` are **check-then-act sequences** without sufficient locking. Given the range `(0, 10)`, concurrent `setLower(5)` and `setUpper(4)` can both pass their checks and both write, producing `(5, 4)` — an invalid state. When variables participate in an invariant, per-variable atomics aren't enough.

Q: When is it safe to publish an underlying state variable directly to clients?
A: When all three hold: 1) the variable is **thread-safe**, 2) it **doesn't participate in invariants** that constrain its value, and 3) no operation on it has **prohibited state transitions**. `Counter.value` fails (2); `VisualComponent.mouseListeners` passes all three.

Q: What problem does `SafePoint.get()` returning `int[]{x, y}` solve?
A: It gives an **atomic combined read** of both coordinates. Separate `getX()` and `getY()` would let another thread mutate the point between the two calls, so a caller could observe an `(x, y)` pair the vehicle was never at. Related values must be fetched atomically.

Q: What is the private constructor capture idiom, and why does `SafePoint` use it?
A: A copy constructor `this(p.x, p.y)` would race between the two reads. `SafePoint` instead has a private constructor taking `int[]`; the public copy constructor calls `this(p.get())`, doing a single atomic snapshot of both coordinates before passing them to the private one.

Q: What's the difference between returning an unmodifiable live view vs. a snapshot of a map?
A: **Live view** (`Collections.unmodifiableMap(backingMap)`) — read-only but reflects ongoing updates; fresh data, but can be inconsistent if callers iterate while it mutates. **Snapshot** (`Collections.unmodifiableMap(new HashMap<>(backingMap))`) — a point-in-time copy; consistent but goes stale. Pick deliberately.

Q: What are the four strategies for adding a new atomic operation to an existing thread-safe class, from safest to most fragile?
A: 1) **Modify the original class** (safest — policy stays in one place). 2) **Extend** the class (`BetterVector`) — fragile, locking code spans files. 3) **Client-side locking** — even more fragile; locking code in an unrelated class. 4) **Composition** (`ImprovedList`) — wrap with your own lock; independent of the underlying locking strategy. Composition is the preferred approach.

Q: Why does the naive `ListHelper` in Listing 4.14 fail to make `putIfAbsent` atomic?
A: It synchronizes on the **wrong lock** — the helper itself, not the underlying list. The list's own `synchronized` methods use the list's intrinsic lock; the helper's `synchronized` blocks don't interact with it, so other threads can modify the list between the `contains` check and the `add`.

Q: How does client-side locking work, and what's its main caveat?
A: You acquire the **same lock the target object uses** (e.g. `synchronized (list)` for a `Collections.synchronizedList`) and perform your compound action inside. Caveat: it only works if the target class **documents** that it supports client-side locking on its intrinsic lock — otherwise you're guessing at its internal policy.

Q: Why is composition the preferred way to add functionality to an existing thread-safe class?
A: The composition wrapper uses **its own lock** to synchronize every delegated method, making it independent of the underlying class's locking strategy. It's effectively the Java monitor pattern over an existing object — works whether the underlying object is thread-safe or not, and doesn't break if the underlying class changes its policy.

Q: What should you document about a thread-safe class, for whom?
A: **For clients**: thread-safety guarantees — is it thread-safe, does it do callbacks under a lock, which lock (if any) supports client-side locking? **For maintainers**: the synchronization policy — which fields are `@GuardedBy` which lock, which invariants hold, which operations are atomic.

Q: Which of `ServletContext`, `HttpSession`, `DataSource`, `SimpleDateFormat`, and JDBC `Connection` are thread-safe, and why?
A: **`ServletContext`, `HttpSession`, `DataSource`** — yes, by reasonable inference (containers access them concurrently and no spec shows client-side locking). **Attributes stored in `ServletContext`/`HttpSession`** — you must make them so. **`SimpleDateFormat`** — **not** thread-safe (classic gotcha). **JDBC `Connection`** — not intended to be shared; typically thread-confined per request.
