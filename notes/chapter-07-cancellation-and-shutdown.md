# Chapter 7: Cancellation and Shutdown

## Key Concepts

Chapter 6 was about *starting* tasks. Chapter 7 is about *stopping* them — gracefully, predictably, without leaving shared state corrupted, queues stuck, or daemons trapping the JVM. The thesis: **Java does not provide a safe way to forcibly stop a thread.** The deprecated `Thread.stop` and `Thread.suspend` tried, and were fundamentally unsafe. What Java offers instead is **interruption** — a cooperative protocol where one thread asks another to stop, and the recipient decides when, and how cleanly, to honor the request.

Cooperative cancellation is required because abrupt termination can leave invariants broken — half-updated data structures, half-written log lines, half-completed transactions. The task itself usually knows best what cleanup is required, so the right model is: external code requests cancellation; the task chooses the cancellation point.

> **"Java does not provide any mechanism for safely forcing a thread to stop what it is doing. Instead, it provides interruption, a cooperative mechanism that lets one thread ask another to stop what it is doing."** (p. 85)

> **"Dealing well with failure, shutdown, and cancellation is one of the characteristics that distinguish a well-behaved application from one that merely works."** (p. 85)

### 7.1 Task Cancellation

A task is **cancellable** if external code can move it to completion before its normal end. Common reasons to cancel:

- **User-requested** — UI cancel button, JMX call.
- **Time-limited activities** — search up to N seconds, then return best so far.
- **Application events** — N tasks search a problem space; first to find a result cancels the rest.
- **Errors** — disk full, dependent service down — cancel siblings, save state.
- **Shutdown** — application is going down; cancel in-flight work, gracefully or abruptly.

A cancellable task needs a **cancellation policy**: how others request cancellation, when the task checks, and what it does on detection. Without a written policy, cancellation tends to be wishful thinking.

#### 7.1.1 The Volatile-Flag Approach (Listing 7.1)

The simplest cooperative protocol — a `volatile boolean cancelled` polled in the task loop:

```java
@ThreadSafe
public class PrimeGenerator implements Runnable {
    @GuardedBy("this") private final List<BigInteger> primes = new ArrayList<>();
    private volatile boolean cancelled;

    public void run() {
        BigInteger p = BigInteger.ONE;
        while (!cancelled) {
            p = p.nextProbablePrime();
            synchronized (this) { primes.add(p); }
        }
    }
    public void cancel() { cancelled = true; }
    public synchronized List<BigInteger> get() { return new ArrayList<>(primes); }
}
```

`volatile` is essential — without it the cancelling thread's write may never be visible to the runner. This works fine for **CPU-bound loops** that check the flag often.

**Where it breaks:** call any blocking method inside the loop (`BlockingQueue.put`, `Thread.sleep`, ...) and the flag never gets checked. A producer blocked on a full queue stays blocked forever, even after `cancel()` is called. Listing 7.3's `BrokenPrimeProducer` is exactly this pitfall — a flag-based task wedged inside a `put`.

#### 7.1.2 Interruption (Listings 7.4, 7.5)

The fix is **thread interruption**. Each thread has a boolean *interrupted status*; setting it is how you tell a thread "stop at your next opportunity."

```java
public class Thread {
    public void interrupt()             { ... }   // set the flag
    public boolean isInterrupted()      { ... }   // read the flag
    public static boolean interrupted() { ... }   // read AND clear
}
```

Three things matter about the API:

1. **`interrupt()` does not stop the thread.** It only sets a flag. The target decides what to do.
2. **Blocking library methods that support interruption** (`Thread.sleep`, `Object.wait`, `Future.get`, `BlockingQueue.put`/`take`, `Lock.lockInterruptibly`, NIO operations) **clear the flag and throw `InterruptedException`** when interrupted. The exception, not the flag, is the carrier of the signal.
3. **The static `Thread.interrupted()` is a footgun.** It both reads *and clears* the flag. If you call it and don't act, you've silently swallowed the signal.

`PrimeProducer` (Listing 7.5) — the corrected version — uses interruption:

```java
class PrimeProducer extends Thread {
    private final BlockingQueue<BigInteger> queue;
    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            while (!Thread.currentThread().isInterrupted())
                queue.put(p = p.nextProbablePrime());   // also throws InterruptedException
        } catch (InterruptedException consumed) {
            /* allow thread to exit */
        }
    }
    public void cancel() { interrupt(); }
}
```

Two cancellation points per loop: the `isInterrupted()` poll at the top, and the blocking `put`. The poll isn't strictly needed because `put` is interruptible, but it makes the producer responsive *before* starting the lengthy `nextProbablePrime` work.

> **"Interruption is usually the most sensible way to implement cancellation."** (p. 88)

> **"Calling `interrupt` does not necessarily stop the target thread from doing what it is doing; it merely delivers the message that interruption has been requested."** (p. 88)

#### 7.1.3 Interruption Policies

Just as a *task* has a cancellation policy, a *thread* has an **interruption policy**: what does the thread do when it sees an interrupt?

The dominant idiom: **service-level cancellation** — exit as quickly as practical, clean up, optionally notify an owner. Other policies (e.g. "pause the service") exist but require tasks written knowing about them.

The crucial separation:

- **Tasks** don't own their threads — they borrow them from a pool. A task that catches `InterruptedException` and doesn't propagate it has stolen a signal that wasn't addressed to it.
- **Threads** own themselves — only the thread's owner (the pool, the service, the framework) is allowed to set a non-default interruption policy.

> **"Because each thread has its own interruption policy, you should not interrupt a thread unless you know what interruption means to that thread."** (p. 89)

Practical rule: **library/task code preserves the interrupt; only the thread's owner clears it.** If you catch `InterruptedException` and aren't the owner, restore the flag with `Thread.currentThread().interrupt()` before returning.

> **"Only code that implements a thread's interruption policy may swallow an interruption request. General-purpose task and library code should never swallow interruption requests."** (p. 89)

#### 7.1.4 Responding to InterruptedException (Listings 7.6, 7.7)

Two options when you catch `InterruptedException`:

**1. Propagate.** Add it to your `throws` clause and let it bubble up. Easiest, and often correct:

```java
public Task getNextTask() throws InterruptedException {
    return queue.take();
}
```

**2. Restore and continue.** When propagation isn't possible (e.g. you're inside `Runnable.run`, which can't throw checked exceptions), preserve the signal so callers higher up can act:

```java
catch (InterruptedException e) {
    Thread.currentThread().interrupt();   // restore — don't swallow
}
```

What you must **not** do is silently catch and discard, unless you are deliberately *implementing* the thread's interruption policy (and then you're typically about to exit anyway).

**Non-cancellable code that calls interruptible methods** has a subtler version (Listing 7.7): if you can't propagate *and* can't exit, save the interrupted-ness locally, retry the blocking call, and restore the flag at the very end:

```java
public Task getNextTask(BlockingQueue<Task> queue) {
    boolean interrupted = false;
    try {
        while (true) {
            try { return queue.take(); }
            catch (InterruptedException e) { interrupted = true; /* retry */ }
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt();
    }
}
```

Restoring the flag too early would cause `take()` to immediately re-throw — an infinite loop.

#### 7.1.5 Cancellation via Future (Listing 7.10)

Rolling your own timed-cancellation logic (Listings 7.8, 7.9) is tricky:
- Listing 7.8 (`timedRun` v1) — schedules an interrupt against the **caller's** thread. Disastrous: if the task finishes early, the interrupt fires after `timedRun` returned, hitting whatever code is running next on that thread.
- Listing 7.9 (v2) — runs the task on a dedicated thread and uses `join` with a timeout. Better, but `join` has no return value distinguishing "completed" from "timed out."

The right answer uses the existing primitive — `Future`:

```java
public static void timedRun(Runnable r, long timeout, TimeUnit unit)
        throws InterruptedException {
    Future<?> task = taskExec.submit(r);
    try {
        task.get(timeout, unit);
    } catch (TimeoutException e) {
        // task will be cancelled below
    } catch (ExecutionException e) {
        throw launderThrowable(e.getCause());
    } finally {
        task.cancel(true);   // harmless if already done
    }
}
```

Key points:

- `Future.cancel(boolean mayInterruptIfRunning)` returns whether cancellation was *delivered*, not whether the task *honored* it.
- `cancel(true)` interrupts the running thread; `cancel(false)` only prevents an unstarted task from starting.
- **It is safe to call `cancel(true)` on tasks running in a standard `Executor`** — those threads have an interruption policy that supports cancellation.
- Calling `cancel` on a completed task is a no-op, so the unconditional `finally` cancel is safe.

> **"When `Future.get` throws `InterruptedException` or `TimeoutException` and you know that the result is no longer needed by the program, cancel the task with `Future.cancel`."** (p. 92)

#### 7.1.6 Non-interruptible Blocking (Listing 7.11)

Some blocking operations **don't respond to interrupt**. The flag gets set, but the thread stays blocked. Knowing the four common cases is essential.

| Blocked on | What to do |
|---|---|
| Synchronous socket I/O (`InputStream.read`, `OutputStream.write`) | **Close the socket.** Blocked thread throws `SocketException`. |
| `java.nio.InterruptibleChannel` | `interrupt()` actually works — throws `ClosedByInterruptException` and closes the channel. Closing the channel from another thread throws `AsynchronousCloseException` to anyone blocked on it. |
| `Selector.select` | `wakeup()` returns it early, possibly with `ClosedSelectorException`. |
| Intrinsic `synchronized` lock acquisition | **Nothing.** Cannot interrupt. Use `ReentrantLock.lockInterruptibly()` (chapter 13) instead. |

`ReaderThread` (Listing 7.11) is the canonical pattern — encapsulate the nonstandard cancellation by overriding `interrupt`:

```java
public class ReaderThread extends Thread {
    private final Socket socket;
    private final InputStream in;

    public ReaderThread(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
    }

    @Override
    public void interrupt() {
        try { socket.close(); }
        catch (IOException ignored) { }
        finally { super.interrupt(); }
    }

    public void run() {
        try {
            byte[] buf = new byte[BUFSZ];
            while (true) {
                int n = in.read(buf);
                if (n < 0) break;
                if (n > 0) processBuffer(buf, n);
            }
        } catch (IOException e) { /* allow thread to exit */ }
    }
}
```

Now `interrupt()` does *both* things: closes the socket (unblocks `read`) and sets the standard flag.

#### 7.1.7 Encapsulating Nonstandard Cancellation with `newTaskFor` (Listing 7.12)

Java 6 added a hook on `ThreadPoolExecutor`:

```java
protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable);
```

Override it to give a task its own customized `Future`. That `Future`'s `cancel()` can do extra work — close a socket, log a metric — beyond standard interruption:

```java
public interface CancellableTask<T> extends Callable<T> {
    void cancel();
    RunnableFuture<T> newTask();
}

public class CancellingExecutor extends ThreadPoolExecutor {
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableTask)
            return ((CancellableTask<T>) callable).newTask();
        return super.newTaskFor(callable);
    }
}
```

The `SocketUsingTask` in the book closes a socket on `cancel()` — the same trick as `ReaderThread`, but on the task side.

### 7.2 Stopping a Thread-based Service

Application code shouldn't directly manipulate threads it doesn't own. A service that owns threads (a thread pool, a logger, a crawler) should expose **lifecycle methods** — `start`, `stop` — and propagate shutdown into the threads it owns.

> **"As with any other encapsulated object, thread ownership is not transitive: the application may own the service and the service may own the worker threads, but the application doesn't own the worker threads and therefore should not attempt to stop them directly."** (p. 93)

> **"Provide lifecycle methods whenever a thread-owning service has a lifetime longer than that of the method that created it."** (p. 93)

#### 7.2.1 Logging Service: Wrong Way and Right Way (Listings 7.13–7.16)

`LogWriter` (Listing 7.13) is a producer-consumer: producers `put` log strings on a `BlockingQueue`; a single logger thread `take`s them off and writes to a `PrintWriter`. No shutdown.

The naïve fix — set `shutdownRequested` and re-check inside `log()` (Listing 7.14):

```java
public void log(String msg) throws InterruptedException {
    if (!shutdownRequested) queue.put(msg);   // race condition!
    else throw new IllegalStateException("logger is shut down");
}
```

This is **check-then-act**. A producer could observe `shutdownRequested == false`, get pre-empted, and the consumer could shut down before the producer reaches `put` — leaving the producer blocked on a queue no one will ever drain.

The real fix (Listing 7.15) is to make submission atomic — add a "reservation" counter under the lock so the consumer knows how many in-flight `put`s it must still wait for:

```java
public class LogService {
    @GuardedBy("this") private boolean isShutdown;
    @GuardedBy("this") private int reservations;

    public void stop() {
        synchronized (this) { isShutdown = true; }
        loggerThread.interrupt();
    }

    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            if (isShutdown) throw new IllegalStateException();
            ++reservations;
        }
        queue.put(msg);   // outside the lock — put can block
    }
    // logger thread loops until isShutdown && reservations == 0
}
```

The lock is held only for the check + reservation; the blocking `put` happens outside it. This is a recurring pattern: **lock the decision, not the I/O.**

`LogService` v2 (Listing 7.16) is even better — delegate everything to a `newSingleThreadExecutor`:

```java
public class LogService {
    private final ExecutorService exec = newSingleThreadExecutor();
    public void stop() throws InterruptedException {
        try {
            exec.shutdown();
            exec.awaitTermination(TIMEOUT, UNIT);
        } finally {
            writer.close();
        }
    }
    public void log(String msg) {
        try { exec.execute(new WriteTask(msg)); }
        catch (RejectedExecutionException ignored) { }
    }
}
```

Same producer-consumer shape; the `Executor` framework handles the lifecycle, queuing, and rejection. Don't reinvent these.

#### 7.2.2 ExecutorService Shutdown — Recap

`shutdown()` vs `shutdownNow()` (introduced in chapter 6) is the **safety vs responsiveness** tradeoff:

| | Behavior | Safety | Responsiveness |
|---|---|---|---|
| `shutdown()` | Stop accepting; finish queued and running | Tasks finish cleanly | May wait long |
| `shutdownNow()` | Drain queue (return unstarted), interrupt running | Running tasks may abort mid-update | Fast |

#### 7.2.3 Poison Pills (Listings 7.17–7.19)

For producer-consumer services with **unbounded queues** and **known counts**, a *poison pill* is a sentinel object placed on the queue meaning "stop after this." The consumer compares each item to the pill and exits when it sees one.

```java
private static final File POISON = new File("");

// CrawlerThread (producer) — Listing 7.18
public void run() {
    try { crawl(root); }
    catch (InterruptedException e) { /* fall through */ }
    finally {
        while (true) {
            try { queue.put(POISON); break; }
            catch (InterruptedException e) { /* retry */ }
        }
    }
}

// IndexerThread (consumer) — Listing 7.19
public void run() {
    try {
        while (true) {
            File file = queue.take();
            if (file == POISON) break;
            indexFile(file);
        }
    } catch (InterruptedException consumed) { }
}
```

Generalizes to N producers / M consumers, but each producer must place the right number of pills (or use a coordinator). **Pills only work with unbounded queues** — on a bounded queue the producer can be blocked on `put` before it gets to enqueue the pill.

#### 7.2.4 One-Shot Execution Service (Listing 7.20)

When you need to run a batch of tasks and return only when they're all done, **scope the executor to the method**:

```java
boolean checkMail(Set<String> hosts, long timeout, TimeUnit unit)
        throws InterruptedException {
    ExecutorService exec = Executors.newCachedThreadPool();
    final AtomicBoolean hasNewMail = new AtomicBoolean(false);
    try {
        for (final String host : hosts)
            exec.execute(() -> { if (checkMail(host)) hasNewMail.set(true); });
    } finally {
        exec.shutdown();
        exec.awaitTermination(timeout, unit);
    }
    return hasNewMail.get();
}
```

The executor is born and dies inside `checkMail`. No external lifecycle to manage; the caller sees a method, not a service.

#### 7.2.5 Limitations of `shutdownNow` (Listings 7.21, 7.22)

`shutdownNow` returns the list of tasks that **were submitted but never started**, but says nothing about tasks **that started but did not finish**. If you need the second list (e.g. a web crawler restarting from where it left off), instrument the executor:

```java
public class TrackingExecutor extends AbstractExecutorService {
    private final ExecutorService exec;
    private final Set<Runnable> tasksCancelledAtShutdown =
            Collections.synchronizedSet(new HashSet<>());

    public List<Runnable> getCancelledTasks() {
        if (!exec.isTerminated()) throw new IllegalStateException();
        return new ArrayList<>(tasksCancelledAtShutdown);
    }

    public void execute(final Runnable runnable) {
        exec.execute(() -> {
            try { runnable.run(); }
            finally {
                if (isShutdown() && Thread.currentThread().isInterrupted())
                    tasksCancelledAtShutdown.add(runnable);
            }
        });
    }
}
```

The trick: a task is "cancelled-mid-execution" iff (a) the executor is shutting down and (b) the worker thread's interrupt flag is set when the task's `run` returns. Subtle race: a task that finishes its last instruction *just before* the interrupt is recorded as cancelled even though it really completed. This is **idempotent-tolerable**: fine for crawlers, dangerous for "send money to user X" tasks.

`WebCrawler` (Listing 7.22) uses this to save un-crawled URLs on shutdown so the crawler can be restarted.

### 7.3 Handling Abnormal Thread Termination

A thread that throws an uncaught `RuntimeException` *dies*. In a console app this is loud — stack trace, exit. In a server it can be **silent**: a worker thread vanishes, the pool shrinks by one, the application keeps "working" with reduced capacity. Eventually the pool is empty and it really stops working — but by then the cause is far away.

> **"The leading cause of premature thread death is `RuntimeException`."** (p. 100)

The cure has two parts:

**1. Wrap untrusted task code in try/catch/finally inside the worker** (Listing 7.23):

```java
public void run() {
    Throwable thrown = null;
    try {
        while (!isInterrupted())
            runTask(getTaskFromWorkQueue());
    } catch (Throwable e) {
        thrown = e;
    } finally {
        threadExited(this, thrown);   // notify the pool
    }
}
```

`ThreadPoolExecutor` uses exactly this pattern internally — a thrown task doesn't kill the pool, it just kills that worker, which is then replaced.

**2. Use an `UncaughtExceptionHandler`** (Section 7.3.1) for any thread that escapes (a) — at minimum log it.

#### 7.3.1 UncaughtExceptionHandler (Listings 7.24, 7.25)

```java
public interface Thread.UncaughtExceptionHandler {
    void uncaughtException(Thread t, Throwable e);
}
```

Set per-thread (`thread.setUncaughtExceptionHandler`) or globally (`Thread.setDefaultUncaughtExceptionHandler`). For pools, supply a `ThreadFactory` that installs the handler on each created thread.

```java
public class UEHLogger implements Thread.UncaughtExceptionHandler {
    public void uncaughtException(Thread t, Throwable e) {
        Logger logger = Logger.getAnonymousLogger();
        logger.log(Level.SEVERE, "Thread terminated: " + t.getName(), e);
    }
}
```

> **"In long-running applications, always use uncaught exception handlers for all threads that at least log the exception."** (p. 101)

**Subtle gotcha:** The handler fires **only for tasks submitted via `execute(Runnable)`**. Tasks submitted via `submit(Callable)` capture the throwable inside their `Future` instead — `Future.get()` throws `ExecutionException` wrapping the cause. Same exception, two completely different paths.

### 7.4 JVM Shutdown

Two flavors:

- **Orderly** — last non-daemon thread exits, or `System.exit`, or SIGINT (Ctrl-C). Runs registered shutdown hooks.
- **Abrupt** — `Runtime.halt`, SIGKILL. Hooks do **not** run.

#### 7.4.1 Shutdown Hooks (Listing 7.26)

Unstarted threads registered via `Runtime.getRuntime().addShutdownHook(thread)`. Run concurrently in unspecified order at orderly shutdown.

```java
public void start() {
    Runtime.getRuntime().addShutdownHook(new Thread() {
        public void run() {
            try { LogService.this.stop(); }
            catch (InterruptedException ignored) {}
        }
    });
}
```

Constraints:
- **Thread-safe** — they run alongside any application threads still alive.
- **Defensive** — make no assumptions about whether other services have shut down already.
- **Fast** — they delay JVM exit; the user is waiting.
- **Not interdependent** — hooks run concurrently in arbitrary order, so a hook should not depend on another hook's services.

A best practice when you have several things to clean up: **register one master hook** that runs them sequentially in a defined order, instead of N concurrent hooks.

#### 7.4.2 Daemon Threads

Two thread types: **normal** and **daemon**. The JVM exits when no normal threads are left — daemons are abandoned (no `finally` blocks, no stack unwinding, just halted). Daemon-ness is inherited from the creating thread.

Use cases: house-keeping background tasks (cache eviction, idle ping). **Not** for I/O — your write may be cut mid-stream.

> **"Daemon threads are not a good substitute for properly managing the lifecycle of services within an application."** (p. 103)

#### 7.4.3 Finalizers — Don't

`Object.finalize` is called by the GC before reclamation. Sounds useful for releasing native resources. In practice:

- Run on a JVM-managed thread, so any state they touch needs synchronization.
- No guarantee they run **at all** — let alone *when*.
- Significant performance cost on objects that have one.
- Notoriously hard to write correctly.

> **"Avoid finalizers."** (p. 103)

Use try-with-resources / explicit `close()` methods. The only marginal use case is releasing native handles; even there, `Cleaner` (Java 9+) is far better.

## Putting It Together — The Cancellation Pyramid

Going from low to high level:

| Layer | Mechanism |
|---|---|
| Atomic flag | `volatile boolean cancelled` — fine for tight CPU loops |
| Interruption | `Thread.interrupt()` + `InterruptedException` — works for blocking methods |
| Non-interruptible blocking | Override `interrupt()` to close socket/channel (Listing 7.11) |
| Tasks on a pool | `Future.cancel(true)` — uses the pool's interruption policy |
| Custom cancellation per task | `newTaskFor` + `CancellableTask` (Listing 7.12) |
| Thread-based service | Lifecycle methods + reservation counter or single-thread executor |
| Producer-consumer service | Poison pills (unbounded queue, known counts) |
| Pool that loses tasks on shutdown | `TrackingExecutor` to record cancelled-in-progress |
| Whole JVM | Shutdown hooks (orderly only); daemon threads for housekeeping |

## Gotchas & Pitfalls

- **`Thread.stop` and `Thread.suspend` are deprecated and unsafe** — they can leave shared state corrupted because they don't respect locks or invariants. Never use them.
- **A `volatile` cancel flag stops working the moment the task makes a blocking call.** The flag will never be checked while the task is wedged in `take`/`put`/`sleep`. Use interruption.
- **`Thread.interrupted()` (static) clears the flag.** If you call it and don't act, you've silently swallowed the interrupt. Prefer `Thread.currentThread().isInterrupted()` unless you specifically want to consume the signal.
- **`interrupt()` doesn't stop anything by itself.** It sets a flag; some methods react to it, some don't. Reading "interrupt" as "force-stop" is a common misconception.
- **Don't catch and discard `InterruptedException`** unless you are the thread's owner and about to exit. Restore the flag (`Thread.currentThread().interrupt()`) so callers can react.
- **Don't interrupt a thread you don't own.** You don't know its interruption policy — you might cancel something you didn't mean to (a pool worker mid-task) or trigger behavior the thread didn't anticipate.
- **Synchronous socket I/O is not interruptible.** `interrupt()` won't unblock `InputStream.read`. Close the socket from outside (the `ReaderThread` pattern).
- **Intrinsic locks (`synchronized`) are not interruptible.** A thread blocked waiting for a `synchronized` block cannot be interrupted. Use `ReentrantLock.lockInterruptibly()` if responsiveness is required (chapter 13).
- **`Future.cancel(false)` vs `cancel(true)`** — `false` only blocks unstarted tasks from starting; `true` interrupts a running task. Use `true` for tasks that respect interruption, `false` for those that don't.
- **Calling `cancel` on a completed Future is harmless.** That's why you can put `task.cancel(true)` unconditionally in a `finally` block (Listing 7.10).
- **Check-then-act in shutdown is a race.** "If not shutdown, then put on queue" can have the shutdown happen between the check and the put. Lock around the *decision*, not the I/O.
- **Poison pills require unbounded queues and known producer/consumer counts.** On a bounded queue a producer can block on `put` *before* enqueueing the pill, defeating the protocol.
- **`shutdownNow` returns only unstarted tasks.** It says nothing about which tasks were running and didn't finish. For that, instrument with `TrackingExecutor`.
- **`TrackingExecutor` has an unavoidable race** — a task that completes the millisecond before the worker is interrupted can be misclassified as "cancelled in progress." Tolerable when tasks are idempotent.
- **Tasks via `submit` route exceptions through `Future`, not `UncaughtExceptionHandler`.** Confusing to debug if you only wired up the handler. Either always use `submit` and call `Future.get`, or wire up both.
- **JVM won't exit while non-daemon threads are alive.** A logger thread, a forgotten timer, an un-shutdown executor — any of these traps your process at exit. Always provide a `stop()` and call it.
- **Shutdown hooks run concurrently in unspecified order.** A hook that closes the log file may break a hook that tries to log. Prefer a single ordering hook that calls everything in the right sequence.
- **Daemon threads are abandoned at JVM exit** — no `finally` blocks, no resource cleanup. Reserve them for stateless housekeeping; never use them for I/O.
- **Avoid finalizers.** They run on a JVM-managed thread, may never run, hurt GC performance, and are hard to write correctly. Use explicit `close` and try-with-resources, or `Cleaner` for native handles.

## Page References

- Why cooperative cancellation, not `Thread.stop`: pp. 85-86
- `PrimeGenerator` and the volatile-flag approach: p. 86
- `BrokenPrimeProducer` — flag breaks under blocking I/O: p. 87
- Thread interruption API (`interrupt`, `isInterrupted`, static `interrupted`): pp. 87-88
- `PrimeProducer` — interruption-based cancellation: p. 88
- Interruption policies, distinction between task and thread: pp. 88-89
- Responding to `InterruptedException` (propagate vs restore): pp. 89-90
- Non-cancellable task that restores interruption before exit: p. 90
- Timed run attempts (`timedRun` v1, v2): pp. 90-91
- Cancellation via `Future` (Listing 7.10): pp. 91-92
- Non-interruptible blocking (sockets, NIO, locks): p. 92
- `ReaderThread` — overriding `interrupt` to close a socket: p. 93
- `newTaskFor` and `CancellingExecutor`: pp. 93-94
- Stopping a thread-based service, ownership: p. 93-94
- `LogWriter`, broken shutdown, `LogService` with reservations: pp. 94-96
- `LogService` delegating to `ExecutorService`: pp. 96-97
- Poison pills, `IndexingService`: pp. 97-98
- One-shot execution service (`checkMail`): pp. 98-99
- `TrackingExecutor`, `WebCrawler` example: pp. 99-100
- Abnormal thread termination, `RuntimeException` killing workers: pp. 100-101
- Worker-thread structure (Listing 7.23): p. 101
- `UncaughtExceptionHandler`, `execute` vs `submit` divergence: pp. 101-102
- JVM orderly vs abrupt shutdown: p. 102
- Shutdown hooks: pp. 102-103
- Daemon threads: p. 103
- Finalizers — avoid: p. 103
