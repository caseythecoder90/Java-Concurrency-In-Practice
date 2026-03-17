# Chapter 1: Introduction

## Key Concepts

### A Brief History of Concurrency
Operating systems evolved from running a single program at a time to running multiple **processes** concurrently. A process is an isolated, independently executing program with its own memory space, file handles, and security credentials. The OS allocates resources (CPU time, memory) across processes.

Three motivating factors drove this evolution:
- **Resource utilization** — while one program waits for I/O (disk, network), another can use the CPU. Idle resources are wasted resources.
- **Fairness** — multiple users and programs have equal claim to the machine's resources. It's preferable to let them share via finer-grained time slicing rather than running one to completion before starting the next.
- **Convenience** — it's easier (and more desirable) to write several programs that each do a single task and coordinate, than to write one monolithic program that does everything.

### Threads: Lightweight Processes
Threads are sometimes called **lightweight processes**. They are the basic unit of scheduling by the OS. Every process has at least one thread. Multiple threads within a process **share the process's memory address space on the heap** — they can access the same variables and objects. This makes communication between threads efficient but also dangerous without proper coordination.

### Benefits of Threads

**Exploiting multiple processors** — With multi-core machines, a single-threaded program can only use one core. Multi-threaded programs can run threads across all available cores simultaneously. The book uses the analogy of a newspaper: even if you had an infinite number of reporters, you couldn't get a story written faster with one editor. But two editors can handle two stories at once. Similarly, threads allow multiple units of work to proceed in parallel.

**Simplicity of modeling** — A program that does many things at once is hard to write as a single sequential flow. Threads let you decompose a complex, asynchronous workflow into simpler, synchronous workflows each running in its own thread. Each thread can be written and reasoned about as if it runs in isolation. Think of the difference between managing a kitchen where one chef does everything vs. where each chef handles one station — each station (thread) has a clear, simple job.

**Simplified handling of asynchronous events** — A server application that accepts socket connections can handle each request in its own thread. This turns the complex problem of juggling many simultaneous connections into a simple "handle one request start-to-finish" model per thread. This is exactly what happens in modern frameworks — when a REST call hits a Spring Web application, the servlet container (Tomcat) dispatches the request to a worker thread that handles it synchronously, while other threads handle other incoming requests concurrently.

### Risks of Threads

**Safety hazards** — When multiple threads access shared mutable state without synchronization, bad things happen. The classic example is the `UnsafeSequence` generator where `value++` *looks* atomic but is actually **three operations**: read the current value, add one, write the result back. If two threads interleave these operations, they can both read the same value and both write back the same incremented result — producing a duplicate sequence number instead of two unique ones. This is a **race condition**: the correctness of the program depends on the relative timing of thread execution, which is unpredictable.

**The compiler, hardware, and runtime take liberties** — In the absence of synchronization, the compiler may reorder instructions, the CPU may execute them out of order, and caches may defer writing values to main memory. These optimizations are invisible in single-threaded code but can cause threads to see stale or inconsistent data. Synchronization isn't just about mutual exclusion — it also tells the JVM to ensure visibility of changes across threads.

**Liveness hazards** — A liveness failure occurs when a thread gets stuck and can never make progress. Examples include deadlock (two threads each waiting for the other to release a lock), starvation, and livelock. Safety says "nothing bad happens," liveness says "something good eventually happens."

**Performance hazards** — Threads carry overhead. **Context switches** occur when the OS suspends one thread and resumes another — this requires saving and restoring thread state, flushing caches, and can disrupt locality of reference. Synchronization mechanisms (locks, volatile, etc.) can inhibit compiler optimizations and force memory barriers. Threads improve throughput but when used carelessly, the overhead can outweigh the benefits.

### Frameworks and Concurrency
You don't always choose to write multi-threaded code — frameworks introduce concurrency for you:

- **Servlets and JSPs** — The servlet specification requires that servlets be prepared to be called from multiple threads simultaneously. The container manages a thread pool and dispatches each HTTP request to a thread. Any shared state in a servlet (instance variables, static fields) must be thread-safe.
- **Spring Framework** — Builds on the servlet model. Spring MVC controllers are singletons by default, meaning one instance handles all requests across threads. If a controller holds mutable state, it's a concurrency bug. Spring Boot with embedded Tomcat uses a thread pool (default 200 threads) to handle concurrent API requests.
- **RMI (Remote Method Invocation)** — Allows calling methods on objects running in another JVM. The RMI runtime may invoke remote methods in multiple threads simultaneously, so remote objects must guard against concurrent access just like servlets.

The key takeaway: even if you never create a `Thread` yourself, if your code runs in a framework, it's already concurrent. You have to write thread-safe code.

## Gotchas & Pitfalls
- `value++` is NOT atomic — it's read-modify-write (three operations). Never assume a single line of Java is a single operation.
- Frameworks make your code concurrent whether you asked for it or not — if your class is a servlet, controller, or RMI object, treat instance-level mutable state as a concurrency hazard.
- The absence of synchronization doesn't just risk interleaving — it risks **visibility** failures where a thread literally never sees another thread's writes.

## Page References
- History of concurrency and processes: pp. 1-2
- Threads as lightweight processes, shared heap memory: pp. 2-3
- Benefits — exploiting multiple processors, simplicity of modeling, async events: pp. 3-5
- Risks — UnsafeSequence, race conditions, safety: pp. 5-7
- Compiler/hardware reordering liberties: p. 7
- Liveness and performance hazards, context switches: pp. 7-8
- Frameworks introducing concurrency (Servlets, RMI): pp. 9-10
