# Chapter 1: Introduction — Flashcards

Q: What is a process, and what are the three motivating factors that drove OSes to support multiple concurrent processes?
A: A process is an isolated, independently executing program with its own memory space. The three factors are **resource utilization** (use the CPU while another process waits on I/O), **fairness** (multiple users/programs share resources via time slicing), and **convenience** (easier to write several single-task programs than one monolithic one).

Q: Why are threads sometimes called "lightweight processes"?
A: Because they are the basic unit of scheduling for the OS (like processes), but unlike processes, multiple threads within a process share the same memory address space on the heap, making them cheaper to create and switch between.

Q: What memory do threads within the same process share, and what do they each have independently?
A: Threads share the process's heap memory (variables, objects, data structures). Each thread has its own program counter, stack, and local variables.

Q: How does multi-threading help exploit multiple processors?
A: A single-threaded program can only use one CPU core. With multiple threads, work can be distributed across all available cores for true parallel execution. The newspaper analogy: one editor can only process one story at a time, but two editors can process two stories simultaneously.

Q: How do threads simplify modeling of complex applications?
A: Threads let you decompose a complex asynchronous workflow into simpler synchronous workflows, each running in its own thread. Each thread can be written and reasoned about as if it runs sequentially in isolation.

Q: How do threads simplify handling of asynchronous events? Give a modern example.
A: A server can handle each incoming request in its own thread, turning the complex problem of juggling many connections into a simple "handle one request" model per thread. In Spring Web, Tomcat dispatches each incoming API call to a worker thread from its pool, letting each request be handled synchronously while others are served concurrently.

Q: Why is `value++` not thread-safe?
A: It looks like one operation but is actually three: **read** the current value, **modify** (add one), **write** the result back. Two threads can interleave these steps — both read the same value, both increment, both write back the same result — producing a lost update.

Q: What is a race condition?
A: A race condition occurs when the correctness of a program depends on the relative timing or interleaving of multiple threads. The outcome is nondeterministic — it may work most of the time but fail unpredictably.

Q: What liberties do the compiler, hardware, and runtime take in the absence of synchronization?
A: The compiler may reorder instructions, the CPU may execute them out of order, and caches may defer writing values to main memory. These optimizations are invisible in single-threaded code but can cause threads to see stale or inconsistent state.

Q: What is the difference between a safety hazard and a liveness hazard?
A: **Safety** = "nothing bad ever happens" (no corrupt data, no wrong results). **Liveness** = "something good eventually happens" (the program makes progress, doesn't get stuck). A deadlock is a liveness failure; a race condition is a safety failure.

Q: What is a context switch and how does it affect performance?
A: A context switch is when the OS suspends one running thread and resumes another. It requires saving/restoring thread state, flushing CPU caches, and disrupts locality of reference. Frequent context switches add overhead that can degrade throughput.

Q: Why must servlets and Spring MVC controllers be thread-safe?
A: The servlet container dispatches HTTP requests to threads from a pool, potentially calling the same servlet/controller instance concurrently. Spring controllers are singletons by default — one instance serves all requests. Any mutable instance state is a concurrency hazard.

Q: What is RMI and why does it require thread safety?
A: RMI (Remote Method Invocation) allows calling methods on objects in another JVM. The RMI runtime may invoke remote methods across multiple threads simultaneously, so remote objects must guard shared mutable state just like servlets.

Q: Even if you never create a Thread yourself, why might your code still need to be thread-safe?
A: Frameworks like servlet containers, Spring, and RMI introduce concurrency on your behalf by calling your code from multiple threads. If your code runs inside a framework, it's already concurrent.
