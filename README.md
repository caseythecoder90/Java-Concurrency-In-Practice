# Java Concurrency in Practice — Study Guide

A chapter-by-chapter study companion for [*Java Concurrency in Practice*](https://jcip.net/) by Brian Goetz et al. — widely considered the definitive book on writing correct, performant multithreaded Java code.

This repository contains detailed notes, flashcards, and runnable code examples for each of the book's 16 chapters. Whether you're reading the book for the first time or revisiting it as a reference, this repo is designed to reinforce the concepts and give you hands-on examples to experiment with.

## What's Inside

| Resource | Location | Description |
|----------|----------|-------------|
| **Notes** | `notes/` | Detailed chapter summaries covering key concepts, gotchas, and page references back to the book |
| **Flashcards (Markdown)** | `flashcards/*.md` | Q&A flashcards for quick review |
| **Flashcards (HTML)** | `flashcards/*.html` | Interactive click-to-reveal flashcards — open in a browser for the best study experience |
| **Code Examples** | `src/main/java/` | Runnable Java examples demonstrating each chapter's concepts (e.g., `UnsafeSequence` vs `SafeSequence`) |
| **Tests** | `src/test/java/` | JUnit 5 tests that exercise the code examples and demonstrate thread-safety issues |

## Chapters

| # | Chapter | Status |
|---|---------|--------|
| 1 | Introduction | Done |
| 2 | Thread Safety | In Progress |
| 3 | Sharing Objects | |
| 4 | Composing Objects | |
| 5 | Building Blocks | |
| 6 | Task Execution | |
| 7 | Cancellation and Shutdown | |
| 8 | Applying Thread Pools | |
| 9 | GUI Applications | |
| 10 | Avoiding Liveness Hazards | |
| 11 | Performance and Scalability | |
| 12 | Testing Concurrent Programs | |
| 13 | Explicit Locks | |
| 14 | Building Custom Synchronizers | |
| 15 | Atomic Variables and Nonblocking Synchronization | |
| 16 | The Java Memory Model | |

## Getting Started

### Prerequisites
- Java 17+
- Maven

### Build & Run Tests
```bash
mvn clean test
```

### Using the Flashcards
Open any `flashcards/*.html` file in your browser for an interactive study session — click a question to reveal the answer. The markdown versions (`flashcards/*.md`) are also available for quick reference directly on GitHub.

## Project Structure
```
├── notes/                  # Chapter-by-chapter study notes
├── flashcards/             # Flashcards in markdown and interactive HTML
├── src/
│   ├── main/java/          # Code examples organized by chapter
│   └── test/java/          # JUnit tests for the examples
└── pom.xml
```

## About the Book

*Java Concurrency in Practice* (2006) covers the fundamentals of threads and concurrency in Java, from basic thread safety and synchronization to advanced topics like lock-free data structures and the Java Memory Model. Despite its age, the core principles remain essential for any Java developer working with multithreaded code.
