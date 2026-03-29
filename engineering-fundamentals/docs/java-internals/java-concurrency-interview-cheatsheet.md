# ⚡ Java Concurrency in 5 Minutes — Interview Cheat Sheet

> Quick-fire concurrency concepts. Know these cold for any Java interview.

---

## Why Concurrency?

**One line:** Concurrency lets your program do **multiple things at once** — handle 1000 requests, process files in parallel, keep the UI responsive.

**The Real Answer:** Modern CPUs have 8-64+ cores. Without concurrency, you're using 1 core and wasting the rest.

---

## Thread Basics — 3 Ways to Create

```java
// 1. Extend Thread (❌ avoid — wastes inheritance)
class MyThread extends Thread {
    public void run() { System.out.println("Running"); }
}
new MyThread().start();

// 2. Implement Runnable (✅ better — can extend another class)
Runnable task = () -> System.out.println("Running");
new Thread(task).start();

// 3. Implement Callable (✅ best when you need a return value)
Callable<Integer> task = () -> { return 42; };
Future<Integer> future = executor.submit(task);
int result = future.get(); // blocks until done
```

---

## Thread Lifecycle (Interview Diagram)

```
    NEW ──start()──▶ RUNNABLE ──scheduler──▶ RUNNING
                        ▲                       │
                        │                       ▼
                    notify()              sleep()/wait()
                        │                       │
                        ▲                       ▼
                     WAITING ◀──────────── BLOCKED
                                                │
                                           run() ends
                                                │
                                                ▼
                                          TERMINATED
```

---

## synchronized — The Foundation

```java
// Method-level lock (locks on `this`)
public synchronized void increment() {
    count++;
}

// Block-level lock (finer control)
public void increment() {
    synchronized (this) {
        count++;
    }
}

// Static sync (locks on Class object)
public static synchronized void staticMethod() { }
```

**Interview Q:** *"What does synchronized do?"*  
**A:** Ensures only **one thread** can execute the block at a time. It acquires a **monitor lock** (intrinsic lock) on the object.

---

## volatile — Visibility Guarantee

```java
private volatile boolean running = true;

// Thread 1
while (running) { doWork(); }

// Thread 2
running = false; // Thread 1 sees this immediately
```

**volatile ≠ synchronized:**
- `volatile` = visibility only (no atomicity for compound operations)
- `synchronized` = visibility + atomicity + mutual exclusion

---

## ExecutorService — Thread Pools (Use This!)

```java
// Fixed thread pool (most common)
ExecutorService executor = Executors.newFixedThreadPool(10);

// Submit tasks
executor.submit(() -> processRequest());
Future<String> future = executor.submit(() -> fetchData());

// Shutdown gracefully
executor.shutdown();
executor.awaitTermination(30, TimeUnit.SECONDS);
```

### Pool Types

| Pool | When to Use |
|------|-------------|
| `newFixedThreadPool(n)` | Known workload, I/O-bound tasks |
| `newCachedThreadPool()` | Many short-lived tasks |
| `newSingleThreadExecutor()` | Sequential task execution |
| `newScheduledThreadPool(n)` | Delayed/periodic tasks |
| `newVirtualThreadPerTaskExecutor()` | Java 21+, massive I/O concurrency |

---

## CompletableFuture — Async Pipelines

```java
CompletableFuture.supplyAsync(() -> fetchUser(id))        // async call
    .thenApply(user -> enrichUser(user))                   // transform
    .thenCompose(user -> fetchOrders(user.getId()))         // chain async
    .thenAccept(orders -> sendEmail(orders))                // consume
    .exceptionally(ex -> { log.error("Failed", ex); return null; });

// Combine multiple futures
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> fetchA());
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> fetchB());

CompletableFuture.allOf(f1, f2).thenRun(() -> {
    // Both complete
    String a = f1.join();
    String b = f2.join();
});
```

---

## java.util.concurrent — Key Classes

| Class | What It Does |
|-------|-------------|
| `AtomicInteger/Long` | Lock-free thread-safe counters |
| `ConcurrentHashMap` | Thread-safe HashMap (no full lock) |
| `CopyOnWriteArrayList` | Thread-safe List (copies on write) |
| `CountDownLatch` | Wait for N threads to finish |
| `CyclicBarrier` | N threads wait for each other |
| `Semaphore` | Limit concurrent access (e.g., max 5 DB connections) |
| `ReentrantLock` | More flexible than synchronized |
| `ReadWriteLock` | Multiple readers OR one writer |
| `BlockingQueue` | Producer-consumer pattern |
| `CompletableFuture` | Async programming with chaining |

---

## Locks — synchronized vs ReentrantLock

| Feature | synchronized | ReentrantLock |
|---------|-------------|---------------|
| Syntax | Keyword | Object |
| Try-lock | ❌ | ✅ `tryLock(timeout)` |
| Fairness | ❌ | ✅ `new ReentrantLock(true)` |
| Interruptible | ❌ | ✅ `lockInterruptibly()` |
| Multiple conditions | ❌ | ✅ `lock.newCondition()` |
| Auto-release | ✅ (on exception) | ❌ (must use finally) |

```java
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    // critical section
} finally {
    lock.unlock(); // ALWAYS in finally!
}
```

---

## Virtual Threads (Java 21+) — Game Changer

```java
// Old: Platform threads (expensive, ~1MB stack each)
ExecutorService executor = Executors.newFixedThreadPool(200);

// New: Virtual threads (cheap, millions possible)
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// One virtual thread per task
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> {
            // Each gets its own virtual thread
            return httpClient.send(request);
        });
    }
}
```

**Interview Q:** *"What are virtual threads?"*  
**A:** Lightweight threads managed by the JVM (not OS). You can create **millions** of them. Perfect for I/O-bound workloads (HTTP calls, DB queries). They're mounted/unmounted from platform threads automatically.

---

## Common Problems & Solutions

| Problem | What Happens | Solution |
|---------|-------------|---------|
| **Race Condition** | Two threads modify same data | `synchronized`, `AtomicXxx`, locks |
| **Deadlock** | Threads wait for each other's locks forever | Lock ordering, timeout, tryLock |
| **Starvation** | Thread never gets CPU time | Fair locks, priority tuning |
| **Livelock** | Threads keep responding to each other, no progress | Randomized backoff |
| **Visibility** | Thread doesn't see another's write | `volatile`, `synchronized` |

### Deadlock Example (Classic Interview)

```java
// Thread 1: lock A → lock B
// Thread 2: lock B → lock A   ← DEADLOCK!

// Fix: Always acquire locks in the same order
// Thread 1: lock A → lock B
// Thread 2: lock A → lock B   ← No deadlock
```

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | Thread vs Runnable? | Thread = class (single inheritance limit). Runnable = interface (flexible). |
| 2 | synchronized vs volatile? | synchronized = mutex + visibility. volatile = visibility only. |
| 3 | What is a deadlock? | Two threads waiting for each other's locks. Circular dependency. |
| 4 | How to prevent deadlock? | Lock ordering, tryLock with timeout, avoid nested locks. |
| 5 | What is a thread pool? | Pre-created threads that execute submitted tasks. Avoids thread creation overhead. |
| 6 | Future vs CompletableFuture? | Future blocks on get(). CompletableFuture supports chaining and async composition. |
| 7 | ConcurrentHashMap vs Hashtable? | CHM uses segment/bucket locking (concurrent reads). Hashtable locks entire map. |
| 8 | What is a race condition? | Multiple threads access shared data without synchronization → unexpected results. |
| 9 | CountDownLatch vs CyclicBarrier? | CDL = one-time, threads wait for N completions. CB = reusable, threads wait for each other. |
| 10 | Virtual threads? | JVM-managed lightweight threads (Java 21+). Millions possible. Best for I/O-bound work. |

---

## Quick Reference

```
Thread     = Unit of execution
Runnable   = Task without return value
Callable   = Task with return value
Future     = Handle to async result
Executor   = Thread pool that runs tasks
Lock       = Manual synchronization
Atomic     = Lock-free thread-safe operations
volatile   = Visibility guarantee across threads
```

