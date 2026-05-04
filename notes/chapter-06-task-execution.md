# Chapter 6: Task Execution

## Key Concepts

Chapter 6 marks the pivot from "how do I make shared state thread-safe" (Part I) to "how do I run work on threads at all" (Part II). The central idea: **submitting tasks should be decoupled from the policy of how they're executed.** Once you stop calling `new Thread(r).start()` directly and start handing work to an `Executor`, you can change the execution policy — pool size, queue, rejection behavior, lifecycle — without touching task code. The chapter develops this through a single example that gets refactored repeatedly: a web server that handles requests.

> **"Frameworks for executing tasks asynchronously [...] are among the most powerful and most useful concurrency utilities in the JDK."** (p. 113)

### 6.1 Executing Tasks in Threads

A *task* is a self-contained unit of work — independent of other tasks, no shared mutable state with them, easy to schedule. Tasks should usually be **small to medium-sized**. Too small, and the overhead of dispatching swamps the work; too large, and you lose parallelism.

The example: **handling HTTP requests in a web server**. Each request is a natural task — independent, bounded, parallelizable.

#### 6.1.1 Sequential Server (Listing 6.1)

```java
while (true) {
    Socket connection = socket.accept();
    handleRequest(connection);   // ← blocks until done
}
```

Correct. Awful in production. While `handleRequest` runs (network I/O, disk I/O, computation), `accept()` is not called. New connections sit in the OS backlog or are refused. Throughput is `1 / mean(handleRequest)` requests per second — a single core, single client at a time.

> **"It is hardly ever a good idea in a production application — it processes only one request at a time and so the server's resources are sorely underutilized."** (p. 114)

#### 6.1.2 Thread-Per-Task (Listing 6.2)

The naïve fix:

```java
while (true) {
    final Socket connection = socket.accept();
    new Thread(() -> handleRequest(connection)).start();
}
```

Better in three ways:
1. The accept loop is no longer blocked behind request handling.
2. Multiple requests run concurrently — useful when each is mostly I/O.
3. Tasks run in parallel on multi-core machines.

But it has serious problems:

- **Thread creation/teardown is not free** — startup latency, memory overhead per thread (~512KB-1MB stack on 64-bit, even if mostly empty).
- **Resource consumption is unbounded.** One thread per concurrent request means under burst load you can spawn thousands of threads. Each consumes a stack and a kernel scheduling slot.
- **Stability is at risk.** There's a per-JVM/per-OS limit on threads (often a few thousand). Exceed it and `Thread.start()` throws `OutOfMemoryError` ("unable to create native thread"). Worse, this can happen *under any concurrent load*, not just when handling tasks — the whole JVM gets unstable.
- **Throughput plateaus and then degrades** as thread count grows past the number of cores: more context-switching, more lock contention, more GC pressure, less work done per unit time.

> **"Within certain ranges, creating more threads improves throughput, but beyond these ranges creating more threads just slows down your application, and creating one thread too many can cause your entire application to crash horribly."** (p. 116)

The takeaway: thread-per-task is fine for prototypes, demos, and bounded-input batch jobs, but **production needs bounded thread counts** — and a way to queue or reject excess work when limits are reached.

### 6.2 The `Executor` Framework

Java's answer: the `Executor` interface and the surrounding `java.util.concurrent` framework.

```java
public interface Executor {
    void execute(Runnable command);
}
```

A one-method SPI. Tiny but transformative. Submission is now decoupled from execution; the same code that says `executor.execute(task)` can run with:

- a thread-per-task executor (`ThreadPerTaskExecutor`)
- a fixed-size pool (`Executors.newFixedThreadPool`)
- a same-thread "executor" (`WithinThreadExecutor`) — runs inline; useful for tests
- a custom `ThreadPoolExecutor` with bespoke queue, rejection policy, threading

This is also a textbook **producer-consumer**: submitting tasks are producers, worker threads are consumers, and the `Executor` mediates with an internal work queue.

#### 6.2.1 Web Server with `ExecutorService` (Listing 6.3)

```java
private static final int NTHREADS = 100;
private static final Executor exec = Executors.newFixedThreadPool(NTHREADS);

while (true) {
    final Socket connection = socket.accept();
    exec.execute(() -> handleRequest(connection));
}
```

Same shape, completely different runtime characteristics: bounded threads, bounded memory, queueing under load. To change policy (e.g. switch to `newCachedThreadPool` while debugging), change one line.

#### 6.2.2 Execution Policies

When you choose an executor, you're picking a bunch of related questions called the **execution policy**:

- *In what thread will tasks be executed?* (caller's thread, dedicated thread, pool thread)
- *In what order will tasks be executed?* (FIFO, LIFO, priority)
- *How many tasks may run concurrently?*
- *How many tasks may be queued waiting to run?*
- *If the system is overloaded, which task gets dropped, and how is the rejection signalled?*
- *What pre/post actions wrap each task?* (logging, metrics, security context)

The `Executor` abstraction lets these be **policy decisions held in one place**, not scattered across calling code.

> **"Whenever you see code of the form `new Thread(runnable).start()` and you think you might at some point want a more flexible execution policy, seriously consider replacing it with the use of an `Executor`."** (p. 119)

#### 6.2.3 Thread Pools — `Executors` Factory Methods

The `java.util.concurrent.Executors` factory class produces several flavors. Know the differences:

| Factory | Behavior | Good for |
|---|---|---|
| `newFixedThreadPool(n)` | Fixed `n` threads. Idle threads are kept. New work goes to an unbounded queue if all threads busy. | Most server work — predictable resource use. |
| `newCachedThreadPool()` | Unbounded thread count. Idle threads die after 60s. | Many short-lived tasks; **dangerous under sustained burst load** — can create unbounded threads. |
| `newSingleThreadExecutor()` | Exactly one worker thread. Tasks run sequentially in submission order. Replacement worker if it dies. | Serial task processing where order matters; replaces home-grown "task queue thread" loops. |
| `newScheduledThreadPool(n)` | Pool of size `n` for delayed and periodic tasks. | Replacement for `Timer`. |

> **"Using an `Executor` is usually the easiest path to implementing a producer-consumer design in your application."** (p. 120)

#### 6.2.4 Executor Lifecycle (`ExecutorService`)

`Executor` doesn't say anything about shutdown. JVMs don't exit cleanly while non-daemon threads are alive — so a pool that doesn't shut down hangs your process. `ExecutorService` extends `Executor` with the lifecycle the framework actually needs:

```java
void shutdown();              // graceful: stop accepting, finish queued
List<Runnable> shutdownNow(); // attempt cancel running, drain unstarted
boolean isShutdown();
boolean isTerminated();       // every task has finished
boolean awaitTermination(long timeout, TimeUnit unit);
<T> Future<T> submit(Callable<T> task);
```

Three lifecycle states: **running → shutting down → terminated**. Once shut down, submission of new tasks throws `RejectedExecutionException` (default policy) — though some custom policies merely log or run-in-caller.

A graceful shutdown idiom:

```java
exec.shutdown();
if (!exec.awaitTermination(30, TimeUnit.SECONDS)) {
    exec.shutdownNow();   // forceful
    if (!exec.awaitTermination(30, TimeUnit.SECONDS))
        log.warn("executor did not terminate");
}
```

`LifecycleWebServer` (Listing 6.8) wraps the server's accept loop around this lifecycle: a `stop()` method calls `exec.shutdown()`; the `try/catch (RejectedExecutionException)` after `accept()` makes shutdown observable to the loop.

> **"`ExecutorService` extends `Executor`, adding methods for lifecycle management."** (p. 121)

#### 6.2.5 Delayed and Periodic Tasks — Goodbye `Timer`

`java.util.Timer` predates the `Executor` framework and has two booby traps:

1. **Single thread.** Long tasks delay every other timer task; periodic tasks may overlap their period.
2. **Unchecked exceptions terminate the timer thread.** A task that throws kills the entire `Timer` — pending tasks never run, *and there's no notification*. The `Timer` looks alive but isn't.

`OutOfTime` (Listing 6.9) demonstrates: a 1-second one-shot task that throws kills the `Timer`, and the second task scheduled for 1 second later never runs.

The replacement: **`ScheduledThreadPoolExecutor`** (or `Executors.newScheduledThreadPool`):

- Multiple threads, so one slow/throwing task doesn't starve others.
- Exceptions propagate to `ThreadPoolExecutor.afterExecute` (override-able) or are wrapped in a `Future` that surfaces them on `get`.
- Same `schedule`, `scheduleAtFixedRate`, `scheduleWithFixedDelay` API.

> **"`Timer`s are sensitive to changes in the system clock, `ScheduledThreadPoolExecutor` isn't."** (p. 124)

### 6.3 Finding Exploitable Parallelism

`Executor` runs `Runnable`s — but `Runnable` has no return value and can't throw checked exceptions. For real work you usually want **results** and **errors**. That's `Callable` + `Future`.

```java
public interface Callable<V> {
    V call() throws Exception;
}

public interface Future<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, ExecutionException, CancellationException;
    V get(long timeout, TimeUnit unit) throws ...;
}
```

`ExecutorService.submit(Callable)` returns `Future<V>`. Calling `get()` blocks until the result is available, throwing `ExecutionException` (wrapping the original cause) if the task failed.

#### 6.3.1 Sequential Page Renderer (Listing 6.10)

A page renderer that downloads images one at a time and renders text only after all images are loaded:

```java
List<ImageData> imageData = new ArrayList<>();
for (ImageInfo i : scanForImageInfo(source))
    imageData.add(i.downloadImage());   // ← serial network I/O
renderText(source);
for (ImageData data : imageData) renderImage(data);
```

The CPU is idle most of the time waiting on network. Page-load latency is the **sum** of the download times. Painful UX.

#### 6.3.2 Page Renderer with `Future` (Listing 6.11/6.12)

Refactor: kick off image downloads in the background while the main thread renders text:

```java
ExecutorService exec = Executors.newCachedThreadPool();
List<ImageInfo> info = scanForImageInfo(source);
Future<List<ImageData>> task = exec.submit(() -> {
    List<ImageData> result = new ArrayList<>();
    for (ImageInfo i : info) result.add(i.downloadImage());
    return result;
});
renderText(source);                              // overlaps download
try {
    for (ImageData data : task.get()) renderImage(data);
} catch (ExecutionException e) {
    throw launderThrowable(e.getCause());
}
```

Better — text renders immediately. But all image downloads are still serialized inside the single submitted task, so the user sees the text and then waits for the bundle. The next refactor unbundles them.

#### 6.3.3 `CompletionService` — Render As Each Image Arrives (Listing 6.13/6.15)

You want to render each image **as soon as its download finishes**, not after the slowest. Polling each `Future` in a loop is awful (busy-waiting or arbitrary delays). Don't write that code — use `CompletionService`:

```java
public interface CompletionService<V> {
    Future<V> submit(Callable<V> task);
    Future<V> take() throws InterruptedException;       // blocks for next done
    Future<V> poll();
    Future<V> poll(long timeout, TimeUnit unit);
}
```

`ExecutorCompletionService` is a thin wrapper around an `Executor` that maintains an internal `BlockingQueue` of completed `Future`s. As each task finishes, its `Future` is enqueued — `take()` returns them **in order of completion, not submission**.

```java
CompletionService<ImageData> cs = new ExecutorCompletionService<>(exec);
for (ImageInfo info : scanForImageInfo(source))
    cs.submit(info::downloadImage);
renderText(source);

try {
    for (int t = 0, n = info.size(); t < n; t++) {
        Future<ImageData> f = cs.take();           // next-finished
        ImageData img = f.get();
        renderImage(img);
    }
} catch (...) { ... }
```

Each image renders the moment it's ready — a far better UX than the bundled `Future` version.

> **"Multiple `ExecutorCompletionService`s can share a single `Executor`, so it is perfectly sensible to create an `ExecutorCompletionService` that is private to a particular computation while sharing a common `Executor`."** (p. 130)

#### 6.3.4 Time-Bounded Tasks

Some tasks shouldn't be allowed to take forever. Two tools:

**Per-task timeout** — `Future.get(timeout, TimeUnit)`. Throws `TimeoutException` if the task hasn't finished. **Always cancel the task on timeout** — otherwise it keeps running, consuming resources for results no one will ever read:

```java
try {
    return task.get(timeout, TimeUnit.MILLISECONDS);
} catch (TimeoutException e) {
    return defaultValue;
} finally {
    task.cancel(true);   // interrupt if still running
}
```

**Group timeout — `invokeAll`** — submit a list of `Callable`s with a single deadline. After the deadline, any unfinished tasks are cancelled and their `Future`s end up in the returned list with `isCancelled() == true`. `Future.get()` on a cancelled Future throws `CancellationException`.

```java
List<Future<TravelQuote>> futures =
    exec.invokeAll(quoteTasks, deadline, TimeUnit.MILLISECONDS);
List<TravelQuote> quotes = new ArrayList<>();
for (Future<TravelQuote> f : futures) {
    try {
        quotes.add(f.isCancelled() ? new TravelQuote("timed out") : f.get());
    } catch (ExecutionException e) {
        quotes.add(quoteForError(e));
    } catch (CancellationException e) {
        quotes.add(new TravelQuote("cancelled"));
    }
}
```

The travel-portal example (Listing 6.17) is the canonical use case: ask several airlines for a quote, give them all (say) 1 second, then return whatever you got.

> **"Tasks that are submitted to an `Executor` must be considered to have a fundamentally asynchronous lifecycle."** (p. 131)

## Putting It Together — Web-Server Evolution

The same web server, three ways:

| Version | Concurrency | Resource bound | Production ready? |
|---|---|---|---|
| `SingleThreadWebServer` | 1 request at a time | Trivial | No — terrible throughput |
| `ThreadPerTaskWebServer` | All concurrent | None — OOMs under burst | No — unstable |
| `TaskExecutionWebServer` (fixed pool) | Up to `NTHREADS` | Bounded threads + queue | **Yes** — predictable |
| `LifecycleWebServer` | Up to `NTHREADS`, with shutdown | Bounded + clean exit | **Yes** + can be stopped |

## Gotchas & Pitfalls

- **`new Thread(r).start()` is a code smell** outside of bootstrapping or one-shot scripts. Use an `Executor`.
- **`newCachedThreadPool` is unbounded.** Under sustained burst load it creates threads without limit and OOMs. Prefer `newFixedThreadPool` or a hand-tuned `ThreadPoolExecutor` for servers.
- **JVMs don't exit while non-daemon threads exist.** A pool you forget to shut down keeps your process alive. Always `shutdown()` (or use `Runtime.addShutdownHook`).
- **`shutdown()` doesn't cancel running tasks.** It just stops accepting new ones. Use `shutdownNow()` to attempt to interrupt running work — and even that is cooperative (the task must respond to interrupt).
- **`shutdownNow()` returns the list of unstarted tasks**, not running ones — useful for handoff to a recovery handler.
- **`Timer` is dangerous.** A throwing task kills the entire `Timer`; one slow task delays every other. Use `ScheduledThreadPoolExecutor` instead.
- **`Future.get()` returns `ExecutionException` wrapping the original cause.** The cause is the actual error — log/handle it with `e.getCause()`, not `e`. Use a `launderThrowable` helper to unwrap cleanly.
- **Polling `Future.isDone()` in a loop is almost always wrong.** Use `Future.get()` (blocking) or `CompletionService.take()` (blocking on first finished).
- **Always cancel timed-out tasks.** A `TimeoutException` from `Future.get(timeout, ...)` does not cancel the underlying task — it just stops waiting. Call `future.cancel(true)` in `finally`.
- **`invokeAll` returns `Future`s in submission order**, but they're not necessarily completed in that order. Check `isCancelled()` before `get()` if you used a timeout.
- **`Executor` doesn't propagate exceptions** for `execute(Runnable)` — uncaught exceptions go to the thread's `UncaughtExceptionHandler`. For visibility, prefer `submit(...)` (returns `Future`) or use `ThreadPoolExecutor.afterExecute`.
- **Don't share state between tasks unless you have to.** The whole framework is built around **independent** tasks. Sharing reintroduces all of Part I's hazards.
- **Pool sizing matters.** Too few threads underutilize CPU; too many waste memory and increase contention. CPU-bound work: ~`N_CPU + 1`. I/O-bound: depends on wait/compute ratio (covered in chapter 8).
- **Don't queue without bound forever.** `newFixedThreadPool` uses an unbounded `LinkedBlockingQueue` by default — under sustained overload, the queue grows without limit. For real backpressure, build a `ThreadPoolExecutor` with a bounded queue and a sensible `RejectedExecutionHandler`.

## Page References

- Sequential vs thread-per-task web server: pp. 113-117
- Disadvantages of unbounded thread creation (lifecycle, resource consumption, stability): pp. 116-117
- The `Executor` framework, decoupling submission from execution: pp. 117-119
- Execution policies: p. 119
- Thread pool factory methods (`newFixedThreadPool`, `newCachedThreadPool`, etc.): p. 120
- `ExecutorService` lifecycle, `shutdown` vs `shutdownNow`: pp. 121-123
- `LifecycleWebServer`: pp. 122-123
- `Timer` problems and `ScheduledThreadPoolExecutor`: pp. 123-124
- Sequential page renderer: pp. 125-126
- `Callable`, `Future`, page renderer with `Future`: pp. 125-128
- `CompletionService`, `ExecutorCompletionService`: pp. 129-130
- Page renderer with `CompletionService`: p. 130
- Time-bounded `Future.get`, travel portal: pp. 131-133
- `invokeAll` with timeout: pp. 132-133