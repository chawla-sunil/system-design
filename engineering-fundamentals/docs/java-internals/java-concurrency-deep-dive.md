# 🧵 Java Concurrency Deep Dive — Senior Engineer's Complete Reference

> Everything a senior Java engineer should know about concurrency.  
> From thread basics to virtual threads, lock-free algorithms, and production patterns.

---

## Table of Contents

1. [Why Concurrency Matters](#1-why-concurrency-matters)
2. [Threads — Creation, Lifecycle, Daemon](#2-threads--creation-lifecycle-daemon)
3. [Thread Safety — The Core Problem](#3-thread-safety--the-core-problem)
4. [synchronized — Intrinsic Locks](#4-synchronized--intrinsic-locks)
5. [volatile — Memory Visibility](#5-volatile--memory-visibility)
6. [Java Memory Model (JMM) — Happens-Before](#6-java-memory-model-jmm--happens-before)
7. [Locks — ReentrantLock, ReadWriteLock, StampedLock](#7-locks--reentrantlock-readwritelock-stampedlock)
8. [Atomic Classes — Lock-Free Concurrency](#8-atomic-classes--lock-free-concurrency)
9. [ExecutorService — Thread Pools](#9-executorservice--thread-pools)
10. [Future, Callable, and CompletableFuture](#10-future-callable-and-completablefuture)
11. [Concurrent Collections](#11-concurrent-collections)
12. [Synchronizers — CountDownLatch, CyclicBarrier, Semaphore, Phaser](#12-synchronizers)
13. [BlockingQueue — Producer-Consumer](#13-blockingqueue--producer-consumer)
14. [Fork/Join Framework](#14-forkjoin-framework)
15. [Virtual Threads (Java 21+)](#15-virtual-threads-java-21)
16. [Structured Concurrency (Java 21+ Preview)](#16-structured-concurrency-java-21-preview)
17. [Common Concurrency Problems](#17-common-concurrency-problems)
18. [Concurrency Patterns](#18-concurrency-patterns)
19. [Testing Concurrent Code](#19-testing-concurrent-code)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. Why Concurrency Matters

### The Hardware Reality

```
Year 2005: CPU = 1 core @ 3.8 GHz
Year 2024: CPU = 16 cores @ 3.5 GHz

Single-threaded code uses 1 core.
15 cores sit idle.
Concurrency uses ALL cores.
```

### When You Need Concurrency

| Scenario | Why |
|----------|-----|
| Web server handling 1000 requests | Each request = separate thread |
| Processing a batch of 1M records | Split across cores |
| Making 10 API calls | Do them in parallel, not sequential |
| UI applications | Background thread for computation, UI thread stays responsive |
| Real-time systems | Multiple data streams processed simultaneously |

---

## 2. Threads — Creation, Lifecycle, Daemon

### Three Ways to Create a Thread

```java
// 1. Extend Thread (avoid — wastes single inheritance)
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println(Thread.currentThread().getName());
    }
}
new MyThread().start();  // start(), not run()!

// 2. Implement Runnable (✅ preferred)
Runnable task = () -> System.out.println("Hello from " + Thread.currentThread().getName());
Thread t = new Thread(task, "worker-1");
t.start();

// 3. Implement Callable<V> (✅ when you need a result)
Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 42;
};
ExecutorService exec = Executors.newSingleThreadExecutor();
Future<Integer> future = exec.submit(task);
System.out.println(future.get());  // 42 (blocks until done)
```

**Interview Q:** *"Why `start()` and not `run()` directly?"*  
**A:** `start()` creates a new OS thread and calls `run()` in that thread. Calling `run()` directly executes it in the **current** thread — no concurrency.

### Thread Lifecycle — States

```
         ┌──────────────────────────────────────────────────────┐
         │                                                      │
    ┌────▼────┐   start()   ┌───────────┐  scheduler  ┌───────┐
    │   NEW   │────────────▶│ RUNNABLE  │────────────▶│RUNNING│
    └─────────┘             └───────────┘             └───┬───┘
                                  ▲                       │
                                  │                       │ sleep()/wait()
                            notify()/                     │ lock contention
                            notifyAll()                   │
                                  │                       ▼
                            ┌─────┴─────┐          ┌──────────┐
                            │  WAITING  │          │ BLOCKED  │
                            │ TIMED_    │          │          │
                            │ WAITING   │          └──────────┘
                            └───────────┘
                                                        │
                                                   run() completes
                                                        │
                                                        ▼
                                                 ┌──────────────┐
                                                 │  TERMINATED  │
                                                 └──────────────┘
```

### Java Thread States (Thread.State enum)

| State | When |
|-------|------|
| `NEW` | Thread created, not yet started |
| `RUNNABLE` | Running or ready to run |
| `BLOCKED` | Waiting to acquire a monitor lock |
| `WAITING` | Waiting indefinitely (`wait()`, `join()`, `park()`) |
| `TIMED_WAITING` | Waiting with timeout (`sleep()`, `wait(timeout)`) |
| `TERMINATED` | Run method completed |

### Daemon Threads

```java
Thread t = new Thread(() -> {
    while (true) { cleanup(); }
});
t.setDaemon(true);  // JVM exits even if this thread is running
t.start();
```

**Daemon threads** run in the background (GC, finalizers). JVM shuts down when only daemon threads remain.

### Thread Priority

```java
t.setPriority(Thread.MAX_PRIORITY);   // 10
t.setPriority(Thread.NORM_PRIORITY);  // 5 (default)
t.setPriority(Thread.MIN_PRIORITY);   // 1

// WARNING: Priority is a HINT to the scheduler. Not guaranteed.
```

---

## 3. Thread Safety — The Core Problem

### What Is Thread Safety?

A class is **thread-safe** if it behaves correctly when accessed from multiple threads, regardless of scheduling or interleaving.

### The Classic Race Condition

```java
// NOT thread-safe
class Counter {
    private int count = 0;
    
    public void increment() {
        count++;  // This is NOT atomic!
    }
    // count++ is actually:
    // 1. READ count from memory
    // 2. ADD 1
    // 3. WRITE back to memory
    // Another thread can interleave between any of these steps
}

// Thread 1: READ count=0, ADD → 1
// Thread 2: READ count=0, ADD → 1  (still sees 0!)
// Thread 1: WRITE count=1
// Thread 2: WRITE count=1  (should be 2!)
```

### How to Achieve Thread Safety

| Approach | When to Use |
|----------|-------------|
| **Immutability** | Best option. Immutable objects are always thread-safe. |
| **synchronized** | Simple mutual exclusion |
| **volatile** | Single variable visibility (no compound operations) |
| **Atomic classes** | Lock-free counters, references |
| **Locks (ReentrantLock)** | Advanced locking (try-lock, fairness) |
| **Concurrent collections** | Thread-safe data structures |
| **ThreadLocal** | Per-thread isolated data |
| **Confinement** | Keep data within one thread |

---

## 4. synchronized — Intrinsic Locks

### How synchronized Works

Every Java object has an **intrinsic lock** (monitor lock). `synchronized` acquires this lock.

```java
// Instance method — locks on `this`
public synchronized void add(int value) {
    this.count += value;
}

// Static method — locks on the Class object
public static synchronized void staticAdd() {
    staticCount++;
}

// Block — locks on specified object (finer granularity)
private final Object lock = new Object();
public void add(int value) {
    synchronized (lock) {
        count += value;
    }
}
```

### Reentrant Nature

```java
public synchronized void outer() {
    inner(); // Same thread can re-enter the lock
}

public synchronized void inner() {
    // Works fine — synchronized is reentrant
}
```

### wait() and notify() — Inter-Thread Communication

```java
// Producer-Consumer with wait/notify
class Buffer {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity = 10;
    
    public synchronized void produce(int item) throws InterruptedException {
        while (queue.size() == capacity) {
            wait();  // Release lock and wait
        }
        queue.add(item);
        notifyAll();  // Wake up consumers
    }
    
    public synchronized int consume() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();  // Release lock and wait
        }
        int item = queue.poll();
        notifyAll();  // Wake up producers
        return item;
    }
}
```

**Interview Q:** *"Why use `while` instead of `if` with `wait()`?"*  
**A:** **Spurious wakeups.** A thread can wake up without being notified. The `while` loop re-checks the condition.

**Interview Q:** *"Why `notifyAll()` instead of `notify()`?"*  
**A:** `notify()` wakes only ONE waiting thread (random). If the wrong thread wakes up and can't proceed, deadlock can occur. `notifyAll()` wakes ALL, and they re-check the condition.

---

## 5. volatile — Memory Visibility

### The Visibility Problem

```
CPU Core 1 (Thread 1)          CPU Core 2 (Thread 2)
┌─────────────┐                ┌─────────────┐
│ L1 Cache    │                │ L1 Cache    │
│ flag = true │                │ flag = false │ ← stale!
└──────┬──────┘                └──────┬──────┘
       │                              │
       └──────────┬───────────────────┘
                  ▼
        ┌─────────────────┐
        │   Main Memory   │
        │   flag = true   │
        └─────────────────┘
```

Each CPU core has its own cache. Without `volatile`, Thread 2 may never see Thread 1's write.

### What volatile Does

```java
private volatile boolean running = true;
```

1. **Every write** to `running` is immediately flushed to main memory
2. **Every read** of `running` goes directly to main memory (no cache)
3. Prevents instruction reordering around the volatile variable

### When to Use volatile

```java
// ✅ Good: Simple flag
private volatile boolean shutdown = false;

// ✅ Good: Singleton double-checked locking
private static volatile Singleton instance;

// ❌ Bad: Compound operation (NOT atomic)
private volatile int count = 0;
count++;  // READ + INCREMENT + WRITE — not safe!
// Use AtomicInteger instead
```

### volatile vs synchronized

| Feature | volatile | synchronized |
|---------|---------|-------------|
| Visibility | ✅ | ✅ |
| Atomicity | ❌ (single read/write only) | ✅ |
| Blocking | ❌ (no lock) | ✅ (acquires lock) |
| Use case | Simple flags, DCL | Compound operations |

---

## 6. Java Memory Model (JMM) — Happens-Before

The JMM defines rules about when one thread's write is guaranteed to be **visible** to another thread.

### Happens-Before Rules (Interview Must-Know)

| Rule | Guarantee |
|------|-----------|
| **Program order** | Each action in a thread happens-before the next action in that thread |
| **Monitor lock** | Unlock happens-before subsequent lock on the same monitor |
| **volatile** | Write to volatile happens-before subsequent read of that volatile |
| **Thread start** | `thread.start()` happens-before any action in the started thread |
| **Thread join** | All actions in a thread happen-before `join()` returns |
| **Transitivity** | If A happens-before B, and B happens-before C, then A happens-before C |

### Instruction Reordering

```java
// Compiler/CPU may reorder instructions for performance
int a = 1;     // Line 1
int b = 2;     // Line 2
// Compiler may execute Line 2 before Line 1 (no dependency)

// volatile prevents reordering across the volatile access
volatile boolean ready = false;
int data = 0;

// Thread 1
data = 42;          // Must happen before volatile write
ready = true;       // volatile write — memory barrier

// Thread 2
if (ready) {        // volatile read — memory barrier
    print(data);    // Guaranteed to see 42
}
```

---

## 7. Locks — ReentrantLock, ReadWriteLock, StampedLock

### ReentrantLock

```java
private final ReentrantLock lock = new ReentrantLock();

public void transfer(Account from, Account to, int amount) {
    lock.lock();
    try {
        from.debit(amount);
        to.credit(amount);
    } finally {
        lock.unlock();  // ALWAYS in finally
    }
}
```

### tryLock — Avoid Deadlocks

```java
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try {
        // critical section
    } finally {
        lock.unlock();
    }
} else {
    // Lock not acquired — handle gracefully
    log.warn("Could not acquire lock, skipping");
}
```

### Fair Lock — FIFO Ordering

```java
// Longest-waiting thread gets the lock next
ReentrantLock fairLock = new ReentrantLock(true);
// Default (false) = non-fair = higher throughput but possible starvation
```

### Condition — Replace wait/notify

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notFull = lock.newCondition();
private final Condition notEmpty = lock.newCondition();

public void produce(int item) throws InterruptedException {
    lock.lock();
    try {
        while (queue.size() == capacity) {
            notFull.await();  // Like wait()
        }
        queue.add(item);
        notEmpty.signal();  // Like notify()
    } finally {
        lock.unlock();
    }
}
```

### ReadWriteLock — Multiple Readers OR One Writer

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

public String read(String key) {
    rwLock.readLock().lock();
    try {
        return cache.get(key);  // Multiple threads can read simultaneously
    } finally {
        rwLock.readLock().unlock();
    }
}

public void write(String key, String value) {
    rwLock.writeLock().lock();
    try {
        cache.put(key, value);  // Exclusive access
    } finally {
        rwLock.writeLock().unlock();
    }
}
```

### StampedLock (Java 8+) — Optimistic Reading

```java
private final StampedLock sl = new StampedLock();

// Optimistic read — no locking overhead if no writer
public double distanceFromOrigin() {
    long stamp = sl.tryOptimisticRead();  // Non-blocking!
    double currentX = x, currentY = y;
    if (!sl.validate(stamp)) {            // Check if a write happened
        stamp = sl.readLock();            // Fall back to read lock
        try {
            currentX = x;
            currentY = y;
        } finally {
            sl.unlockRead(stamp);
        }
    }
    return Math.sqrt(currentX * currentX + currentY * currentY);
}
```

---

## 8. Atomic Classes — Lock-Free Concurrency

### AtomicInteger / AtomicLong

```java
AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();     // ++counter (atomic)
counter.getAndIncrement();     // counter++ (atomic)
counter.addAndGet(5);          // counter += 5 (atomic)
counter.compareAndSet(5, 10);  // CAS: if value==5, set to 10

// How CAS works (pseudo-code):
// loop {
//     current = get()
//     next = current + 1
//     if (compareAndSet(current, next)) break  // no lock, just CPU instruction
// }
```

### AtomicReference — Lock-Free Object Updates

```java
AtomicReference<User> userRef = new AtomicReference<>(initialUser);

// Update atomically
userRef.updateAndGet(user -> user.withName("New Name"));

// Compare and set
User expected = userRef.get();
User updated = new User("Updated");
userRef.compareAndSet(expected, updated);
```

### LongAdder — High-Contention Counter (Java 8+)

```java
// Better than AtomicLong under high contention
LongAdder adder = new LongAdder();
adder.increment();      // Different threads update different cells
adder.add(10);
long total = adder.sum(); // Aggregate all cells

// Why faster? AtomicLong = all threads CAS on one value (contention)
// LongAdder = threads update separate cells, sum when needed
```

---

## 9. ExecutorService — Thread Pools

### Why Thread Pools?

Creating a thread is expensive (~1MB stack, OS thread creation). Thread pools reuse threads.

### ThreadPoolExecutor — The Real Constructor

```java
// This is what ALL Executors.newXxx() methods create internally
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                          // corePoolSize — always alive
    20,                         // maximumPoolSize — max threads
    60L, TimeUnit.SECONDS,      // keepAliveTime — idle thread lifetime
    new LinkedBlockingQueue<>(1000),  // workQueue — pending tasks
    new ThreadFactory() {             // threadFactory — naming
        private final AtomicInteger count = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "worker-" + count.incrementAndGet());
        }
    },
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejectionHandler
);
```

### How Thread Pool Works

```
Task submitted
     │
     ▼
Core threads busy?
     │
     ├── NO → Execute in core thread
     │
     └── YES → Queue full?
                  │
                  ├── NO → Add to queue
                  │
                  └── YES → Max threads reached?
                               │
                               ├── NO → Create new thread
                               │
                               └── YES → Rejection handler
```

### Rejection Policies

| Policy | Behavior |
|--------|----------|
| `AbortPolicy` | Throws `RejectedExecutionException` (default) |
| `CallerRunsPolicy` | Caller thread runs the task (backpressure) |
| `DiscardPolicy` | Silently discards the task |
| `DiscardOldestPolicy` | Discards oldest queued task, submits new one |

### Pool Sizing Rules

```
CPU-bound tasks:  poolSize = number of CPUs (Runtime.getRuntime().availableProcessors())
I/O-bound tasks:  poolSize = number of CPUs × (1 + wait_time / compute_time)

Example for I/O-bound:
- 8 CPUs
- Each task: 100ms compute, 900ms waiting for I/O
- Pool size = 8 × (1 + 900/100) = 80 threads
```

### ScheduledExecutorService

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

// Run once after 5 seconds
scheduler.schedule(() -> cleanup(), 5, TimeUnit.SECONDS);

// Run every 10 seconds (fixed rate — includes execution time)
scheduler.scheduleAtFixedRate(() -> poll(), 0, 10, TimeUnit.SECONDS);

// Run with 10-second delay between end of one and start of next
scheduler.scheduleWithFixedDelay(() -> poll(), 0, 10, TimeUnit.SECONDS);
```

---

## 10. Future, Callable, and CompletableFuture

### Future — Basic Async

```java
ExecutorService exec = Executors.newFixedThreadPool(4);
Future<String> future = exec.submit(() -> {
    Thread.sleep(2000);
    return "Hello";
});

// Blocking get
String result = future.get();                    // Blocks forever
String result = future.get(5, TimeUnit.SECONDS); // Blocks with timeout

future.isDone();     // Check completion
future.cancel(true); // Cancel (may interrupt)
future.isCancelled();
```

### CompletableFuture — Modern Async (Java 8+)

```java
// Create async tasks
CompletableFuture<User> userFuture = CompletableFuture.supplyAsync(() -> userService.findById(id));
CompletableFuture<List<Order>> ordersFuture = CompletableFuture.supplyAsync(() -> orderService.findByUser(id));

// Transform results
CompletableFuture<String> nameFuture = userFuture
    .thenApply(user -> user.getName())           // sync transform
    .thenApply(name -> name.toUpperCase());

// Chain async calls
CompletableFuture<List<Order>> result = userFuture
    .thenCompose(user -> orderService.findByUserAsync(user.getId()));  // async chain

// Combine two futures
CompletableFuture<String> combined = userFuture.thenCombine(
    ordersFuture,
    (user, orders) -> user.getName() + " has " + orders.size() + " orders"
);

// Wait for all
CompletableFuture.allOf(userFuture, ordersFuture)
    .thenRun(() -> System.out.println("Both done!"));

// Wait for any (first to complete)
CompletableFuture.anyOf(future1, future2)
    .thenAccept(result -> System.out.println("First result: " + result));

// Error handling
CompletableFuture.supplyAsync(() -> riskyOperation())
    .thenApply(result -> process(result))
    .exceptionally(ex -> {
        log.error("Failed", ex);
        return fallbackValue;
    })
    .whenComplete((result, ex) -> {
        // Called regardless of success or failure
        if (ex != null) log.error("Error", ex);
        else log.info("Result: {}", result);
    });
```

### CompletableFuture Method Cheat Sheet

| Method | What It Does |
|--------|-------------|
| `supplyAsync(Supplier)` | Start async task with return value |
| `runAsync(Runnable)` | Start async task without return value |
| `thenApply(Function)` | Transform result (sync) |
| `thenCompose(Function)` | Chain async call (flatMap) |
| `thenAccept(Consumer)` | Consume result (no return) |
| `thenRun(Runnable)` | Run after completion (no access to result) |
| `thenCombine(CF, BiFunction)` | Combine two futures |
| `allOf(CF...)` | Wait for all to complete |
| `anyOf(CF...)` | Wait for first to complete |
| `exceptionally(Function)` | Handle exception |
| `handle(BiFunction)` | Handle result or exception |
| `whenComplete(BiConsumer)` | Callback on completion |

> Methods ending in `Async` (e.g., `thenApplyAsync`) run the transformation in a different thread.

---

## 11. Concurrent Collections

### ConcurrentHashMap (Most Important)

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

// Basic operations (all thread-safe)
map.put("key", 1);
map.get("key");
map.remove("key");

// Atomic compound operations
map.putIfAbsent("key", 1);
map.computeIfAbsent("key", k -> expensiveCompute(k));
map.computeIfPresent("key", (k, v) -> v + 1);
map.merge("key", 1, Integer::sum);  // Add 1, or set to 1 if absent

// Parallel operations (Java 8+)
map.forEach(2, (key, value) -> process(key, value));  // parallelism = 2
long count = map.reduceValues(4, Long::sum);
```

**Interview Q:** *"How does ConcurrentHashMap achieve concurrency?"*  
**A:**
- **Java 7:** Segment-based locking (16 segments by default)
- **Java 8+:** Node-level locking (CAS for first node in bucket, `synchronized` for others) + Red-black trees for long chains

### CopyOnWriteArrayList

```java
CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();
list.add("item");  // Creates a new copy of the array

// ✅ Great for: Read-heavy, write-rare scenarios (e.g., listener lists)
// ❌ Bad for: Frequent writes (each write copies the entire array)
```

### ConcurrentLinkedQueue / ConcurrentLinkedDeque

```java
ConcurrentLinkedQueue<Task> queue = new ConcurrentLinkedQueue<>();
queue.offer(task);    // Non-blocking add
Task t = queue.poll(); // Non-blocking remove (null if empty)
```

---

## 12. Synchronizers

### CountDownLatch — Wait for N Events

```java
// Main thread waits for 3 services to initialize
CountDownLatch latch = new CountDownLatch(3);

// Each service thread:
executor.submit(() -> {
    initializeDatabase();
    latch.countDown();  // Signal: I'm done
});
executor.submit(() -> {
    initializeCache();
    latch.countDown();
});
executor.submit(() -> {
    initializeQueue();
    latch.countDown();
});

latch.await();  // Main thread blocks until count reaches 0
System.out.println("All services ready!");

// NOTE: CountDownLatch is ONE-TIME. Cannot be reset.
```

### CyclicBarrier — Threads Wait for Each Other

```java
// 3 threads must all reach the barrier before any can proceed
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("All threads reached barrier — proceeding!");
});

for (int i = 0; i < 3; i++) {
    executor.submit(() -> {
        computePartialResult();
        barrier.await();  // Wait for other threads
        // All 3 threads proceed together
        mergeResults();
    });
}

// CyclicBarrier IS reusable (unlike CountDownLatch)
```

### CountDownLatch vs CyclicBarrier

| Feature | CountDownLatch | CyclicBarrier |
|---------|---------------|---------------|
| Reusable | ❌ One-time | ✅ Reusable |
| Who waits | One thread waits for N events | N threads wait for each other |
| Count | Counts down | Counts up to N |
| Action on complete | None | Optional Runnable |

### Semaphore — Limit Concurrent Access

```java
// Allow max 5 concurrent database connections
Semaphore semaphore = new Semaphore(5);

public Connection getConnection() throws InterruptedException {
    semaphore.acquire();  // Block if 5 threads already have permits
    try {
        return connectionPool.getConnection();
    } finally {
        // release in finally of the actual usage, not here
    }
}

public void releaseConnection(Connection conn) {
    connectionPool.release(conn);
    semaphore.release();  // Return permit
}
```

### Phaser — Flexible CyclicBarrier (Java 7+)

```java
Phaser phaser = new Phaser(3);  // 3 parties

// Thread can join/leave dynamically
phaser.register();    // Add a party
phaser.arriveAndDeregister();  // Leave

// Wait for current phase to complete
phaser.arriveAndAwaitAdvance();
```

---

## 13. BlockingQueue — Producer-Consumer

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// Producer
executor.submit(() -> {
    while (running) {
        Task task = generateTask();
        queue.put(task);  // Blocks if queue is full
    }
});

// Consumer
executor.submit(() -> {
    while (running) {
        Task task = queue.take();  // Blocks if queue is empty
        process(task);
    }
});
```

### BlockingQueue Implementations

| Implementation | Bounded | Ordering | Use Case |
|----------------|---------|----------|----------|
| `ArrayBlockingQueue` | ✅ Fixed | FIFO | General purpose |
| `LinkedBlockingQueue` | Optional | FIFO | High throughput |
| `PriorityBlockingQueue` | ❌ Unbounded | Priority | Priority-based processing |
| `SynchronousQueue` | 0 capacity | Direct handoff | Thread pool (newCachedThreadPool) |
| `DelayQueue` | ❌ Unbounded | Delay-based | Scheduled tasks |

---

## 14. Fork/Join Framework

### Divide and Conquer — Parallel Recursion

```java
class SumTask extends RecursiveTask<Long> {
    private final long[] array;
    private final int start, end;
    private static final int THRESHOLD = 10_000;

    @Override
    protected Long compute() {
        if (end - start <= THRESHOLD) {
            // Base case: compute directly
            long sum = 0;
            for (int i = start; i < end; i++) sum += array[i];
            return sum;
        }

        // Split into subtasks
        int mid = (start + end) / 2;
        SumTask left = new SumTask(array, start, mid);
        SumTask right = new SumTask(array, mid, end);

        left.fork();             // Submit left to pool
        long rightResult = right.compute();  // Compute right in current thread
        long leftResult = left.join();       // Wait for left

        return leftResult + rightResult;
    }
}

ForkJoinPool pool = ForkJoinPool.commonPool();
long result = pool.invoke(new SumTask(array, 0, array.length));
```

### Work Stealing

```
Thread 1 queue: [Task A, Task B, Task C]
Thread 2 queue: []  ← Idle!

Thread 2 STEALS Task C from Thread 1's queue (from the tail)
→ Both threads stay busy!
```

### Parallel Streams Use Fork/Join

```java
// This uses ForkJoinPool.commonPool() internally
long sum = Arrays.stream(array)
    .parallel()
    .mapToLong(Long::longValue)
    .sum();

// Custom pool for parallel streams (to avoid blocking common pool)
ForkJoinPool customPool = new ForkJoinPool(4);
long sum = customPool.submit(() ->
    list.parallelStream()
        .mapToLong(this::process)
        .sum()
).get();
```

---

## 15. Virtual Threads (Java 21+)

### The Problem with Platform Threads

```
Platform Thread = 1 OS Thread = ~1MB stack memory

Server with 4GB RAM ≈ 4000 threads max
Each thread handling an HTTP request = 4000 concurrent requests

But most threads are WAITING (for DB, HTTP, file I/O)
They're holding expensive OS resources while doing nothing!
```

### Virtual Threads — The Solution

```java
// Virtual threads are managed by JVM, not OS
// ~1000 bytes per virtual thread (not 1MB!)
// Millions of virtual threads possible

// Create virtual threads
Thread.startVirtualThread(() -> {
    // This runs on a virtual thread
    var response = httpClient.send(request);  // Yields the platform thread during I/O
});

// With executor (recommended)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> {
            // 1 million concurrent tasks!
            return callExternalApi();
        });
    }
}
```

### How Virtual Threads Work

```
Virtual Thread (VT) mounted on Platform Thread (PT)

VT-1 ──── PT-1: executing code...
VT-2 ──── PT-2: executing code...
VT-3 waiting (I/O)          ← unmounted from PT
VT-4 ──── PT-3: executing code...

When VT-1 does I/O (blocking call):
1. VT-1 is unmounted from PT-1
2. VT-5 (from queue) is mounted on PT-1
3. When VT-1's I/O completes, it's mounted on any available PT
```

### Platform Thread vs Virtual Thread

| Feature | Platform Thread | Virtual Thread |
|---------|----------------|----------------|
| Managed by | OS | JVM |
| Stack size | ~1MB | ~few KB (grows as needed) |
| Count | Thousands | Millions |
| Cost | Expensive | Cheap |
| I/O blocking | Wastes OS thread | Yields PT, resumes later |
| Best for | CPU-bound work | I/O-bound work |
| synchronized | Works (but pins PT) | Use ReentrantLock instead |

### When to Use Virtual Threads

```
✅ I/O-bound tasks: HTTP calls, DB queries, file I/O
✅ High concurrency: Handle millions of concurrent requests
✅ Simple thread-per-request model: No need for reactive/async code

❌ CPU-bound tasks: Still limited by number of CPU cores
❌ synchronized blocks: Can "pin" the virtual thread (use ReentrantLock)
❌ ThreadLocal abuse: Millions of VTs = millions of ThreadLocal copies
```

---

## 16. Structured Concurrency (Java 21+ Preview)

```java
// Problem: Unstructured concurrency — tasks can leak
CompletableFuture<User> f1 = CompletableFuture.supplyAsync(() -> fetchUser());
CompletableFuture<Order> f2 = CompletableFuture.supplyAsync(() -> fetchOrder());
// If f1 fails, f2 keeps running! Resource leak.

// Solution: Structured concurrency — scope controls all subtasks
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<User> user = scope.fork(() -> fetchUser());
    Subtask<Order> order = scope.fork(() -> fetchOrder());

    scope.join();           // Wait for both
    scope.throwIfFailed();  // Propagate errors

    // If either fails, the other is cancelled automatically!
    return new Response(user.get(), order.get());
}
```

---

## 17. Common Concurrency Problems

### Deadlock

```java
// Thread 1: lock A → lock B
// Thread 2: lock B → lock A
// Both waiting for each other = DEADLOCK

// Prevention:
// 1. Lock ordering (always A before B)
// 2. tryLock with timeout
// 3. Avoid nested locks
// 4. Use higher-level constructs (ConcurrentHashMap, etc.)
```

### How to Detect Deadlocks

```bash
# Thread dump
jstack <pid>
# or
kill -3 <pid>

# Look for:
# "Found one Java-level deadlock"
# Thread-1 waiting on lock held by Thread-2
# Thread-2 waiting on lock held by Thread-1
```

### Starvation

```java
// Low-priority thread never gets CPU time
// Fix: Fair locks
ReentrantLock lock = new ReentrantLock(true);  // fair = FIFO
```

### False Sharing

```java
// Two threads modify different fields that share a cache line (64 bytes)
// CPU invalidates entire cache line → performance drops

// Fix: Padding (@Contended in JDK internals)
@jdk.internal.vm.annotation.Contended
private volatile long counter1;  // On its own cache line
```

---

## 18. Concurrency Patterns

### Thread-Safe Singleton (Double-Checked Locking)

```java
public class Singleton {
    private static volatile Singleton instance;

    public static Singleton getInstance() {
        if (instance == null) {                    // First check (no lock)
            synchronized (Singleton.class) {
                if (instance == null) {             // Second check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
// volatile is REQUIRED to prevent instruction reordering
```

### Thread-Safe Lazy Initialization (Holder Pattern)

```java
public class Singleton {
    private Singleton() {}

    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;  // Class loaded only on first access
    }
}
// Thread-safe by JVM class loading guarantee. No synchronized needed.
```

### ThreadLocal — Per-Thread Data

```java
// Each thread gets its own copy
ThreadLocal<SimpleDateFormat> formatter =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// Usage
String date = formatter.get().format(new Date());

// IMPORTANT: Clean up in thread pools!
formatter.remove();  // Prevent memory leaks
```

---

## 19. Testing Concurrent Code

```java
// Use CountDownLatch to ensure concurrent execution
@Test
void testConcurrentIncrement() throws InterruptedException {
    Counter counter = new Counter();
    int threadCount = 1000;
    ExecutorService exec = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        exec.submit(() -> {
            counter.increment();
            latch.countDown();
        });
    }

    latch.await(10, TimeUnit.SECONDS);
    assertEquals(1000, counter.get());
}
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | What is thread safety? | Correct behavior when accessed by multiple threads without additional synchronization |
| 2 | synchronized vs ReentrantLock? | ReentrantLock has tryLock, fairness, interruptible, multiple conditions |
| 3 | What is a deadlock? How to prevent? | Circular lock dependency. Prevent: lock ordering, tryLock, avoid nesting |
| 4 | volatile vs synchronized? | volatile = visibility. synchronized = visibility + atomicity + mutual exclusion |
| 5 | What is the Java Memory Model? | Rules defining when one thread's writes are visible to another thread |
| 6 | What is happens-before? | Guarantee that memory writes by one operation are visible to another |
| 7 | ConcurrentHashMap vs Collections.synchronizedMap? | CHM: segment/node locking (concurrent). synchronizedMap: single lock (sequential) |
| 8 | What is CAS? | Compare-And-Swap. CPU instruction for lock-free atomic updates. Used by AtomicXxx. |
| 9 | CountDownLatch vs CyclicBarrier? | CDL: one-time, wait for N events. CB: reusable, N threads wait for each other. |
| 10 | What is a thread pool? Why use it? | Reuse threads. Avoid creation overhead. Control concurrency. |
| 11 | How to size a thread pool? | CPU-bound: cores. I/O-bound: cores × (1 + wait/compute) |
| 12 | Future vs CompletableFuture? | Future: blocking get(). CF: non-blocking, chaining, composition, error handling. |
| 13 | What is Fork/Join? | Divide-and-conquer parallel framework with work-stealing. |
| 14 | What are virtual threads? | JVM-managed lightweight threads (Java 21+). Millions possible. Best for I/O. |
| 15 | When NOT to use virtual threads? | CPU-bound tasks, synchronized blocks (pinning), heavy ThreadLocal usage. |
| 16 | What is thread starvation? | Thread never gets CPU/lock because others dominate. Fix: fair locks. |
| 17 | What is a race condition? | Non-deterministic behavior from uncontrolled thread scheduling. |
| 18 | What is the double-checked locking pattern? | Lazy singleton: check → lock → check → create. Requires volatile. |
| 19 | What is ThreadLocal? | Per-thread storage. Each thread gets its own copy. Must clean up in pools. |
| 20 | What is the producer-consumer pattern? | Producers add to BlockingQueue. Consumers take from it. Decoupled. |
| 21 | What is a Semaphore? | Controls access to a limited number of resources (e.g., connection pool). |
| 22 | ReadWriteLock benefit? | Multiple concurrent readers OR one exclusive writer. Faster for read-heavy. |
| 23 | What is work-stealing? | Idle threads steal tasks from busy threads' queues. Used in ForkJoinPool. |
| 24 | What is structured concurrency? | Subtasks' lifecycle is scoped — failure cancels siblings. Preview in Java 21+. |
| 25 | AtomicInteger vs synchronized counter? | AtomicInteger is lock-free (CAS). Better performance under low contention. |
| 26 | What is a memory barrier? | CPU instruction that enforces ordering of memory operations. volatile inserts one. |
| 27 | What is false sharing? | Two threads modify different data on the same cache line → cache invalidation. |
| 28 | How to detect deadlocks? | jstack thread dump, JMX ThreadMXBean.findDeadlockedThreads() |
| 29 | What is LongAdder? | High-throughput counter. Better than AtomicLong under high contention. |
| 30 | parallel stream caveats? | Uses common ForkJoinPool (shared). Blocking tasks can starve other streams. |

---

> **Pro Tip:** In interviews, always mention: "I'd use the highest-level abstraction that fits — ConcurrentHashMap over manual locking, CompletableFuture over raw threads, virtual threads over thread pools for I/O-bound work."

