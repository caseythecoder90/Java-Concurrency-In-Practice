# Chapter 3: Sharing Objects — Flashcards

Q: Besides atomicity, what second major problem does `synchronized` solve?
A: **Memory visibility.** When thread A releases a lock and thread B later acquires the same lock, everything A did before the release is visible to B after the acquire. Without synchronization, there's no guarantee a reader will ever see another thread's writes.

Q: What are the three things that can go wrong in `NoVisibility` (Listing 3.1)?
A: 1) It could print `42` (the happy path). 2) It could print `0` — the write to `ready` becomes visible before the write to `number` due to **reordering**. 3) It could **loop forever** — the write to `ready` never becomes visible to the reader.

Q: What is stale data, and why is it especially dangerous?
A: A stale value is an old value still visible to a reader after another thread has updated the variable. It's dangerous because staleness is **not all-or-nothing** — a thread can see a fresh value for one field and a stale value for another written first, leaving objects in observably inconsistent states.

Q: What's special about non-volatile `long` and `double` variables across threads?
A: The JVM may split a 64-bit read or write into two 32-bit operations. A reader can see a "torn" value — the high 32 bits from one write and the low 32 bits from another, a value no thread ever wrote. Shared mutable `long`/`double` must be `volatile` or lock-guarded.

Q: What visibility guarantee does `volatile` give that regular variables don't?
A: Reads of a volatile always return the most recent write by any thread — the value is never cached in a register or per-CPU cache hidden from others. Writing a volatile also acts like exiting a synchronized block: all writes made before the volatile write become visible to a thread that reads the volatile.

Q: What can `volatile` **not** do that `synchronized` or atomic classes can?
A: **Atomicity of compound actions.** `count++` on a volatile is still three operations (read, add, write); interleaved threads can lose updates. `volatile` guarantees visibility only — use `AtomicLong` or a lock for read-modify-write.

Q: When is it appropriate to use `volatile`?
A: When all three hold: writes don't depend on the current value (or only one thread writes), the variable doesn't participate in invariants with other state variables, and locking isn't needed for any other reason. Classic use: a completion/shutdown/status flag like `volatile boolean asleep`.

Q: What does it mean for an object to "escape"?
A: It's been made reachable outside its intended scope — stored in a public static field, returned from a non-private method, passed to an alien method, exposed via an inner class, etc. Once escaped, you must assume other code may use or misuse it concurrently.

Q: What is an "alien method" and why does it matter?
A: Any method whose behavior isn't fully specified by your class — methods on other classes, or overridable (non-final, non-private) methods in your own class. Passing an object to an alien method is publication, because you can't know whether it will retain or leak the reference.

Q: Why is `ThisEscape` (Listing 3.7) broken, and how does `SafeListener` fix it?
A: Registering an anonymous inner-class listener from the constructor leaks `this` — the inner class holds an implicit outer reference, so other threads can reach the `ThisEscape` object before construction finishes. `SafeListener` uses a **private constructor + public static factory method** that registers the listener only *after* the object is fully constructed.

Q: What is thread confinement, and why does it make non-thread-safe objects safe?
A: Thread confinement means an object is only accessed by a single thread. Since there's no sharing, there are no race conditions or visibility issues — even non-thread-safe objects are safe to use this way. Examples: Swing components confined to the EDT, pooled JDBC `Connection`s confined for a request's duration.

Q: What's the difference between ad-hoc, stack, and ThreadLocal confinement?
A: **Ad-hoc**: confinement enforced purely by convention/documentation — fragile. **Stack**: object only reachable via local variables, so the stack itself enforces confinement. **ThreadLocal**: `ThreadLocal<T>` gives each thread its own copy via `get`/`set`; the JVM stores values on the `Thread` itself.

Q: Give a realistic use case for `ThreadLocal`.
A: Associating a per-thread database connection so code doesn't pass `Connection` through every method call, or storing transaction context in J2EE containers. Each thread's first `get` calls `initialValue`; subsequent calls return that thread's stored value.

Q: What's the downside of `ThreadLocal`?
A: It behaves like a global variable or hidden parameter, creating invisible coupling between classes. Code using a `ThreadLocal` is implicitly tied to whatever framework populates it, and the coupling isn't visible in method signatures. Use sparingly.

Q: What are the three requirements for an object to be immutable?
A: 1) **State cannot be modified** after construction. 2) **All fields are `final`.** 3) **Proper construction** — the `this` reference does not escape during construction. Note: `final` fields alone aren't sufficient, because a `final` field can reference a mutable object.

Q: Can an immutable object use mutable objects internally?
A: Yes. `ThreeStooges` (Listing 3.11) uses a mutable `HashSet` internally. As long as the mutable state is (a) established entirely in the constructor, (b) never exposed, and (c) never modified afterward, the containing object is immutable.

Q: How do `OneValueCache` and `VolatileCachedFactorizer` (Listings 3.12-3.13) solve the two-field atomicity problem without locking?
A: `OneValueCache` is an **immutable holder** containing both the number and its factors — any thread that sees the reference sees a consistent snapshot. `VolatileCachedFactorizer` keeps a single `volatile OneValueCache cache`; updating is a one-shot reassignment of the volatile field, which is instantly visible to all threads. No lock needed.

Q: Why is `public Holder holder; holder = new Holder(42);` unsafe publication?
A: Without synchronization, other threads may (a) see a stale value for the `holder` field (null or earlier) or (b) see the reference but see `Holder`'s fields in a **partially constructed state** — e.g., the default `0` instead of the constructor-set value. `Holder.assertSanity`'s `if (n != n)` check really can fire.

Q: What are the four safe publication idioms for a properly constructed object?
A: 1) Initialize it from a **static initializer**. 2) Store the reference in a **`volatile` field or `AtomicReference`**. 3) Store the reference in a **`final` field** of a properly constructed object. 4) Store the reference in a field **properly guarded by a lock**.

Q: Do thread-safe collections provide safe publication?
A: Yes. Placing an object into `Hashtable`, `ConcurrentMap`, `synchronizedMap`, `Vector`, `CopyOnWriteArrayList`/`Set`, `synchronizedList`/`Set`, `BlockingQueue`, or `ConcurrentLinkedQueue` safely publishes it to any thread that later retrieves it — no extra synchronization needed at the handoff.

Q: What is an effectively immutable object?
A: An object that isn't technically immutable (it could be mutated), but by convention is never modified after publication. Once safely published, an effectively immutable object can be shared across threads without synchronization. Example: `Date` values stored in a `synchronizedMap` that no one mutates afterward.

Q: What special guarantee does the JMM give to truly immutable objects?
A: **Initialization safety** — they can be safely accessed by any thread **even if synchronization was not used to publish them**. This only applies when all immutability requirements are met (unmodifiable state, all fields `final`, proper construction). A non-immutable object published unsafely is not covered.

Q: What are the four sharing policies the book recommends documenting for any shared object?
A: **Thread-confined** (owned by one thread), **Shared read-only** (immutable or effectively immutable, no writers), **Shared thread-safe** (object synchronizes itself internally), and **Guarded** (accessible only while holding a specified lock). Every shared reference should come with a known policy.

Q: What's the publication requirement difference between immutable, effectively immutable, and mutable objects?
A: **Immutable**: can be published by any mechanism, even a data race. **Effectively immutable**: must be safely published, then no further synchronization is needed. **Mutable**: must be safely published *and* either be thread-safe itself or accessed only with a lock held.
