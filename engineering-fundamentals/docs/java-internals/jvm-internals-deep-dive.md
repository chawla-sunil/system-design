# 🧠 JVM Internals Deep Dive — Senior Engineer's Complete Reference

> Everything a senior Java engineer should know about the JVM.  
> From class loading to GC tuning, JIT compilation, and production troubleshooting.

---

## Table of Contents

1. [What is the JVM — Really?](#1-what-is-the-jvm--really)
2. [JVM Architecture — Complete Picture](#2-jvm-architecture--complete-picture)
3. [Class Loading Subsystem](#3-class-loading-subsystem)
4. [Runtime Data Areas — Memory Layout](#4-runtime-data-areas--memory-layout)
5. [Heap Deep Dive — Generational Memory](#5-heap-deep-dive--generational-memory)
6. [Stack Deep Dive — Frames and Local Variables](#6-stack-deep-dive--frames-and-local-variables)
7. [Metaspace — Class Metadata (Java 8+)](#7-metaspace--class-metadata-java-8)
8. [Garbage Collection — Algorithms and Tuning](#8-garbage-collection--algorithms-and-tuning)
9. [G1 Garbage Collector — Deep Dive](#9-g1-garbage-collector--deep-dive)
10. [ZGC and Shenandoah — Low-Latency GC](#10-zgc-and-shenandoah--low-latency-gc)
11. [JIT Compilation — Interpreter to Native](#11-jit-compilation--interpreter-to-native)
12. [String Pool and Interning](#12-string-pool-and-interning)
13. [Java Memory Model (JMM)](#13-java-memory-model-jmm)
14. [JVM Flags — Complete Reference](#14-jvm-flags--complete-reference)
15. [JVM in Containers (Docker/Kubernetes)](#15-jvm-in-containers-dockerkubernetes)
16. [Monitoring & Profiling Tools](#16-monitoring--profiling-tools)
17. [Troubleshooting — OOM, CPU Spikes, GC Pauses](#17-troubleshooting--oom-cpu-spikes-gc-pauses)
18. [GraalVM and Native Image](#18-graalvm-and-native-image)
19. [JVM Performance Tuning Checklist](#19-jvm-performance-tuning-checklist)
20. [Interview Q&A — 30 Questions](#20-interview-qa--30-questions)

---

## 1. What is the JVM — Really?

The JVM is a **virtual machine** that:
1. **Loads** bytecode (.class files)
2. **Verifies** it for safety
3. **Interprets** it (or compiles it to native code via JIT)
4. **Manages** memory (allocation + garbage collection)
5. **Provides** a runtime environment (threading, security, I/O)

### Write Once, Run Anywhere

```
Java Source (.java)
       │
   javac (compiler)
       │
       ▼
Bytecode (.class)     ← Platform-independent
       │
       ├──── JVM on Linux
       ├──── JVM on macOS
       └──── JVM on Windows
              │
              ▼
         Native execution  ← Platform-specific
```

### JVM Implementations

| Implementation | By | Note |
|----------------|-------|------|
| HotSpot | Oracle/OpenJDK | Most widely used |
| OpenJ9 | Eclipse/IBM | Lower memory footprint |
| GraalVM | Oracle | Polyglot, native image |
| Azul Zing | Azul | Pauseless GC |
| Amazon Corretto | AWS | OpenJDK distribution |
| Eclipse Temurin | Adoptium | Community OpenJDK build |

---

## 2. JVM Architecture — Complete Picture

```
┌──────────────────────────────────────────────────────────────────────┐
│                              JVM                                      │
│                                                                      │
│  ┌────────────────────────┐                                          │
│  │   Class Loader          │                                          │
│  │   Subsystem             │  .class files                            │
│  │   ┌──────────────┐     │◀──────────                               │
│  │   │ Loading      │     │                                          │
│  │   │ Linking      │     │                                          │
│  │   │ Initialization│    │                                          │
│  │   └──────────────┘     │                                          │
│  └───────────┬────────────┘                                          │
│              │                                                        │
│              ▼                                                        │
│  ┌────────────────────────────────────────────────────┐              │
│  │              Runtime Data Areas                      │              │
│  │                                                      │              │
│  │  ┌──────────────────────────────────┐  (Shared)     │              │
│  │  │            HEAP                   │               │              │
│  │  │  ┌────────────┐ ┌──────────────┐ │               │              │
│  │  │  │ Young Gen  │ │   Old Gen    │ │               │              │
│  │  │  │Eden|S0|S1  │ │  (Tenured)   │ │               │              │
│  │  │  └────────────┘ └──────────────┘ │               │              │
│  │  └──────────────────────────────────┘               │              │
│  │                                                      │              │
│  │  ┌─────────────────┐  (Shared)                      │              │
│  │  │   Metaspace      │  Class metadata, method info   │              │
│  │  └─────────────────┘                                 │              │
│  │                                                      │              │
│  │  Per-Thread:                                         │              │
│  │  ┌────────┐ ┌──────────┐ ┌──────────────────┐      │              │
│  │  │ Stack  │ │PC Register│ │Native Method Stack│      │              │
│  │  └────────┘ └──────────┘ └──────────────────┘      │              │
│  └────────────────────────────────────────────────────┘              │
│                                                                      │
│  ┌────────────────────────────────────────────────────┐              │
│  │              Execution Engine                        │              │
│  │  ┌────────────┐  ┌───────────┐  ┌──────────────┐  │              │
│  │  │Interpreter │  │JIT Compiler│  │   GC          │  │              │
│  │  │            │  │ C1 + C2    │  │               │  │              │
│  │  └────────────┘  └───────────┘  └──────────────┘  │              │
│  └────────────────────────────────────────────────────┘              │
│                                                                      │
│  ┌────────────────────────────────────────────────────┐              │
│  │           Native Interface (JNI)                     │              │
│  └────────────────────────────────────────────────────┘              │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 3. Class Loading Subsystem

### Three Phases

```
Loading → Linking → Initialization

Loading:
  Read .class file → create java.lang.Class object

Linking:
  1. Verify   → Bytecode verification (valid format, no illegal access)
  2. Prepare  → Allocate memory for static fields, set default values
  3. Resolve  → Replace symbolic references with direct references

Initialization:
  Execute static initializers and static blocks
  (happens on first active use of the class)
```

### Class Loader Hierarchy (Delegation Model)

```
┌───────────────────────────┐
│  Bootstrap ClassLoader    │  ← Loads core Java classes (rt.jar)
│  (native C code)          │     java.lang.*, java.util.*, etc.
└────────────┬──────────────┘
             │ parent
┌────────────▼──────────────┐
│  Platform ClassLoader     │  ← Loads extension classes
│  (was: Extension)         │     javax.*, java.sql.*, etc.
└────────────┬──────────────┘
             │ parent
┌────────────▼──────────────┐
│  Application ClassLoader  │  ← Loads your classes (classpath)
│  (was: System)            │     com.myapp.*
└────────────┬──────────────┘
             │ parent
┌────────────▼──────────────┐
│  Custom ClassLoaders      │  ← Tomcat, OSGi, Spring Boot
└───────────────────────────┘
```

### How Delegation Works

```
AppClassLoader.loadClass("com.myapp.Service")
  → Delegates to parent: PlatformClassLoader
    → Delegates to parent: BootstrapClassLoader
      → Can't find → returns null
    → PlatformClassLoader can't find → returns null
  → AppClassLoader searches classpath → FOUND!
```

**Interview Q:** *"Why parent delegation?"*  
**A:** Security and uniqueness. Prevents you from creating a fake `java.lang.String` — Bootstrap always loads core classes first.

### When Does a Class Get Loaded?

| Trigger | Example |
|---------|---------|
| New instance | `new MyClass()` |
| Static method call | `MyClass.staticMethod()` |
| Static field access | `MyClass.CONSTANT` |
| Reflection | `Class.forName("MyClass")` |
| Subclass loaded | Loading `Child` loads `Parent` |
| Main class | JVM startup |

---

## 4. Runtime Data Areas — Memory Layout

### Shared Areas (All Threads)

| Area | Contents | Lifecycle |
|------|----------|-----------|
| **Heap** | All objects and arrays | JVM lifetime |
| **Metaspace** | Class metadata, method bytecode, constant pool | JVM lifetime |
| **Code Cache** | JIT-compiled native code | JVM lifetime |

### Per-Thread Areas

| Area | Contents | Lifecycle |
|------|----------|-----------|
| **JVM Stack** | Stack frames (one per method call) | Thread lifetime |
| **PC Register** | Address of current bytecode instruction | Thread lifetime |
| **Native Stack** | Native method (JNI) call frames | Thread lifetime |

---

## 5. Heap Deep Dive — Generational Memory

### Why Generational?

**Observation:** Most objects die young (weak generational hypothesis).

```
Object Survival Rate:
  ~90-95% of objects die in Young Gen (never reach Old Gen)
  
  This means:
  → Collect Young Gen frequently (Minor GC — fast, small area)
  → Collect Old Gen rarely (Major GC — slow, large area)
```

### Heap Layout (G1 perspective)

```
┌─────────────────────────────────────────────────────┐
│                        HEAP                          │
│                                                      │
│  Young Generation (~33% of heap)                     │
│  ┌──────────────┬─────────┬─────────┐               │
│  │    Eden      │   S0    │   S1    │               │
│  │  (new objects)│(survivor)│(survivor)│              │
│  │   ~80%       │  ~10%   │  ~10%   │               │
│  └──────────────┴─────────┴─────────┘               │
│                                                      │
│  Old Generation (~67% of heap)                       │
│  ┌──────────────────────────────────────────┐       │
│  │               Tenured Space               │       │
│  │   (objects that survived many GCs)        │       │
│  └──────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────┘
```

### Object Lifecycle

```
1. Object created → allocated in Eden

2. Eden fills → Minor GC triggers
   ┌─────────┐
   │  Eden   │ → Live objects copied to S0
   │ (full)  │   Dead objects reclaimed
   └─────────┘

3. Next Minor GC:
   Eden + S0 → Live objects copied to S1
   (S0 and S1 swap roles each cycle)

4. Object survives N Minor GCs → Promoted to Old Gen
   (N = tenuring threshold, default 15 for G1)

5. Old Gen fills → Major GC (Full GC)
   Much slower — scans entire heap
   STOP-THE-WORLD pause
```

### Object Allocation — TLAB (Thread-Local Allocation Buffer)

```
Each thread gets a private chunk of Eden (TLAB)
→ No synchronization needed for object allocation
→ Very fast (just bump a pointer)

Thread 1:  [TLAB─────────────]
Thread 2:  [TLAB─────────────]
Thread 3:  [TLAB─────────────]
           ◄──── Eden space ────►
```

---

## 6. Stack Deep Dive — Frames and Local Variables

### Stack Frame Structure

```
┌─────────────────────────────┐
│         Stack Frame          │  ← One per method invocation
│                              │
│  ┌────────────────────────┐ │
│  │  Local Variable Array   │ │  ← Method params + local vars
│  │  [0] = this             │ │
│  │  [1] = param1           │ │
│  │  [2] = localVar         │ │
│  └────────────────────────┘ │
│                              │
│  ┌────────────────────────┐ │
│  │  Operand Stack          │ │  ← Computation workspace
│  │  (push/pop values)      │ │
│  └────────────────────────┘ │
│                              │
│  ┌────────────────────────┐ │
│  │  Frame Data             │ │  ← Return address, exception table
│  │  (constant pool ref)    │ │
│  └────────────────────────┘ │
└─────────────────────────────┘
```

### Stack Example

```java
public int add(int a, int b) {     // Frame pushed onto stack
    int sum = a + b;                // Local vars: [this, a, b, sum]
    return sum;                     // Frame popped from stack
}
```

```
Thread Stack:
┌─────────────────┐  ← Top (current method)
│ add() frame     │
├─────────────────┤
│ process() frame │
├─────────────────┤
│ main() frame    │
└─────────────────┘  ← Bottom
```

### StackOverflowError

```java
// Infinite recursion → stack runs out of space
public void oops() {
    oops();  // Each call adds a frame → StackOverflowError
}

// Default stack size: 512KB - 1MB
// Increase: -Xss2m (but creates bigger threads)
```

---

## 7. Metaspace — Class Metadata (Java 8+)

### PermGen → Metaspace Migration

| Feature | PermGen (Java ≤ 7) | Metaspace (Java 8+) |
|---------|-------|---------|
| Location | Part of Java heap | Native memory (off-heap) |
| Default size | 64-256 MB | Unlimited (auto-grows) |
| OOM Error | `PermGen space` | `Metaspace` |
| Tuning | `-XX:MaxPermSize` | `-XX:MaxMetaspaceSize` |

### What's Stored in Metaspace?

- Class structures (field/method definitions)
- Method bytecode
- Constant pool (strings, numbers, references)
- Annotations
- Method counters (for JIT decisions)

### Metaspace Leak — Common Cause

```
Hot-deploying apps in Tomcat:
1. Deploy app → classes loaded into Metaspace
2. Undeploy app → classes should be GC'd
3. But if any reference holds the ClassLoader alive → classes stay!
4. Deploy again → NEW classes loaded
5. Repeat → Metaspace grows until OOM

Fix: Ensure ClassLoader is GC-eligible on undeploy.
     No static references to webapp classes from shared libs.
```

---

## 8. Garbage Collection — Algorithms and Tuning

### GC Roots — Where GC Starts

```
GC traces from "roots" to find all reachable (live) objects.
Anything not reachable from a root = garbage.

GC Roots:
├── Local variables on thread stacks
├── Active threads
├── Static fields of loaded classes
├── JNI references
└── Synchronized monitors (lock objects)
```

### GC Algorithms

#### Mark-Sweep-Compact

```
1. MARK:    Traverse from GC roots, mark all reachable objects
2. SWEEP:   Reclaim unmarked objects
3. COMPACT: Move surviving objects together (defragment)

Before:  [Live][Dead][Live][Dead][Dead][Live]
After:   [Live][Live][Live][Free──────────────]
```

#### Copying Collector (Young Gen)

```
Before:  S0: [Live A][Dead][Live B][Dead]   S1: [empty]
After:   S0: [empty]                         S1: [Live A][Live B]

Only copies live objects (fast if most are dead — which they are in Young Gen!)
```

### GC Types

| Type | What | When | Impact |
|------|------|------|--------|
| **Minor GC** | Collects Young Gen only | Eden full | Short pause (ms) |
| **Major GC** | Collects Old Gen | Old Gen full | Long pause |
| **Full GC** | Collects entire heap + Metaspace | System.gc(), OOM prevention | Longest pause |
| **Mixed GC** | Young + some Old regions (G1) | G1 scheduling | Medium pause |

### GC Collectors Comparison

| Collector | Algorithm | Pause | Throughput | Use Case |
|-----------|-----------|-------|-----------|----------|
| **Serial** | Mark-Sweep-Compact, single-threaded | Long | Low | Small apps, embedded |
| **Parallel** | Mark-Sweep-Compact, multi-threaded | Medium | High | Batch jobs, throughput |
| **CMS** | Concurrent Mark-Sweep (deprecated Java 14) | Short | Medium | Legacy low-latency |
| **G1** | Region-based, incremental | Short | Good | General purpose (default) |
| **ZGC** | Colored pointers, load barriers | < 1ms | Good | Ultra-low latency |
| **Shenandoah** | Brooks pointers | < 10ms | Good | Low latency |

---

## 9. G1 Garbage Collector — Deep Dive

### Region-Based Architecture

```
G1 divides the heap into equal-sized REGIONS (typically 1-32 MB each)

┌────┬────┬────┬────┬────┬────┬────┬────┐
│ E  │ E  │ S  │    │ O  │ O  │ H  │ O  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│ O  │    │ E  │ O  │    │ O  │ O  │ E  │
├────┼────┼────┼────┼────┼────┼────┼────┤
│    │ O  │ O  │ S  │ E  │    │ O  │    │
└────┴────┴────┴────┴────┴────┴────┴────┘

E = Eden    S = Survivor    O = Old    H = Humongous
(blank) = Free
```

### G1 Collection Phases

```
1. Young-Only Phase:
   - Minor GCs collect Eden + Survivor regions
   - Live objects copied to Survivor or promoted to Old

2. Space Reclamation Phase (Mixed GC):
   - Concurrent marking finds garbage in Old regions
   - Mixed GCs collect: Young + selected Old regions with most garbage
   - "Garbage First" = collect regions with most garbage first (hence the name!)

3. Full GC (fallback):
   - Only if Mixed GC can't keep up
   - Single-threaded, stop-the-world — avoid this!
```

### G1 Tuning

```bash
-XX:+UseG1GC                       # Enable G1 (default Java 9+)
-XX:MaxGCPauseMillis=200            # Target pause time (default 200ms)
-XX:G1HeapRegionSize=4m             # Region size (1-32MB, power of 2)
-XX:InitiatingHeapOccupancyPercent=45  # Start concurrent marking when 45% heap used
-XX:G1ReservePercent=10             # Reserve 10% heap for promotion
-XX:ConcGCThreads=4                 # Concurrent GC threads
-XX:ParallelGCThreads=8             # Parallel GC threads (stop-the-world)
```

### Humongous Objects

Objects larger than 50% of a region are **humongous**:
- Allocated in contiguous regions
- Never moved (expensive to copy)
- Collected during cleanup or Full GC
- **Avoid creating many large short-lived objects!**

---

## 10. ZGC and Shenandoah — Low-Latency GC

### ZGC (Java 15+ Production Ready)

```
Goal: Sub-millisecond pauses regardless of heap size

How: Colored Pointers + Load Barriers

Colored Pointers:
- 64-bit pointers use extra bits as metadata
- Bits indicate: remapped, marked0, marked1, finalizable
- GC can relocate objects while app is running

Load Barrier:
- Every time you load a reference, a barrier checks if it's valid
- If object was relocated, barrier updates the reference transparently

Result:
- GC pause < 1ms (even with terabytes of heap!)
- Concurrent: almost all work done while app runs
- Throughput: ~5-15% overhead for the barriers
```

```bash
-XX:+UseZGC
-XX:+ZGenerational    # Generational ZGC (Java 21+, recommended)
-Xmx16g              # ZGC handles large heaps well
```

### When to Use What

| GC | Heap Size | Pause Target | Throughput |
|----|-----------|-------------|-----------|
| Parallel | Any | Don't care | Maximum |
| G1 | < 64GB | < 200ms | Good |
| ZGC | Any (TB+) | < 1ms | Good |
| Shenandoah | < 64GB | < 10ms | Good |

---

## 11. JIT Compilation — Interpreter to Native

### Execution Pipeline

```
Bytecode → Interpreter → Profile → C1 Compile → C2 Compile
                           │
                    Count method invocations
                    and loop iterations
                           │
                    ┌──────┴───────┐
                    │ Hot Method?  │
                    │ > 10,000     │
                    │ invocations  │
                    └──────┬───────┘
                           │ Yes
                    ┌──────┴───────┐
                    │ JIT Compile  │
                    │ to native    │
                    └──────────────┘
```

### Tiered Compilation (Default)

```
Level 0: Interpreter (bytecode)
Level 1: C1 with full optimization
Level 2: C1 with limited optimization  
Level 3: C1 with full profiling
Level 4: C2 with aggressive optimization ← Maximum performance

Typical path: Level 0 → Level 3 → Level 4
```

### JIT Optimizations

| Optimization | What It Does |
|-------------|-------------|
| **Inlining** | Replace method call with method body (avoids call overhead) |
| **Escape Analysis** | Object doesn't escape method → allocate on stack (no GC!) |
| **Loop Unrolling** | Replace loop with repeated code (avoid branch prediction misses) |
| **Dead Code Elimination** | Remove code that can never execute |
| **Null Check Elimination** | Remove redundant null checks |
| **Lock Elision** | Remove locks when object doesn't escape thread |
| **Lock Coarsening** | Merge adjacent synchronized blocks |

### Escape Analysis — Stack Allocation

```java
// Without escape analysis:
public int sum() {
    Point p = new Point(1, 2);  // Allocated on HEAP, needs GC
    return p.x + p.y;
}

// With escape analysis:
// JIT detects: Point doesn't escape the method
// → Allocates x and y on STACK (or in registers)
// → No heap allocation, no GC needed!
```

---

## 12. String Pool and Interning

```java
String s1 = "hello";              // String pool (heap)
String s2 = "hello";              // Same reference from pool
String s3 = new String("hello");  // New object on heap

s1 == s2      // true  (same pooled reference)
s1 == s3      // false (different objects)
s1.equals(s3) // true  (same content)

// Explicitly intern
String s4 = s3.intern();          // Returns pooled reference
s1 == s4      // true

// String concatenation
String s5 = "hel" + "lo";        // Compile-time constant → pooled
s1 == s5      // true

String part = "lo";
String s6 = "hel" + part;        // Runtime concatenation → NOT pooled
s1 == s6      // false
```

### String Pool Location

| Java Version | Pool Location |
|-------------|---------------|
| Java ≤ 6 | PermGen (fixed, could OOM) |
| Java 7+ | Heap (GC-managed, grows dynamically) |

---

## 13. Java Memory Model (JMM)

### What JMM Defines

The JMM specifies:
- When a thread's write to a variable is **guaranteed visible** to another thread
- What **reorderings** the compiler/CPU are allowed to do

### Happens-Before Relationships

```
These guarantee that memory writes in one statement are visible to reads in another:

1. Program Order: Each action in a thread happens-before the next action
2. Monitor Lock:  unlock() happens-before subsequent lock() on same monitor
3. Volatile:      Write to volatile happens-before subsequent read of same volatile
4. Thread Start:  thread.start() happens-before any action in started thread
5. Thread Join:   All actions in thread happen-before join() returns
6. Transitivity:  If A hb B, and B hb C, then A hb C
```

### Memory Barriers

```
volatile write:
  [StoreStore barrier]  ← Flushes all pending writes to memory
  write volatile field
  [StoreLoad barrier]   ← Prevents reordering with subsequent loads

volatile read:
  read volatile field
  [LoadLoad barrier]    ← Prevents reordering with subsequent loads
  [LoadStore barrier]   ← Prevents reordering with subsequent stores
```

---

## 14. JVM Flags — Complete Reference

### Memory Configuration

```bash
# Heap
-Xms512m                          # Initial heap size
-Xmx4g                            # Maximum heap size
-XX:NewRatio=2                     # Old:Young ratio (Old = 2× Young)
-XX:SurvivorRatio=8                # Eden:Survivor ratio (Eden = 8× Survivor)
-XX:MaxTenuringThreshold=15        # GC cycles before promotion to Old

# Metaspace
-XX:MetaspaceSize=128m             # Initial Metaspace size
-XX:MaxMetaspaceSize=512m          # Max Metaspace size

# Stack
-Xss512k                          # Thread stack size

# Direct Memory
-XX:MaxDirectMemorySize=1g         # For NIO direct buffers
```

### GC Configuration

```bash
# Collector Selection
-XX:+UseG1GC                       # G1 (default Java 9+)
-XX:+UseZGC                        # ZGC (Java 15+)
-XX:+UseShenandoahGC               # Shenandoah
-XX:+UseParallelGC                 # Parallel (throughput)

# G1 Tuning
-XX:MaxGCPauseMillis=200           # Target pause time
-XX:G1HeapRegionSize=4m            # Region size
-XX:InitiatingHeapOccupancyPercent=45

# GC Logging (Java 9+)
-Xlog:gc*:file=gc.log:time,level,tags:filecount=5,filesize=10m
```

### Diagnostic & Debug

```bash
-XX:+HeapDumpOnOutOfMemoryError    # Dump heap on OOM
-XX:HeapDumpPath=/tmp/heapdump.hprof
-XX:OnOutOfMemoryError="kill -9 %p" # Kill JVM on OOM
-XX:+PrintFlagsFinal               # Print all JVM flags
-XX:NativeMemoryTracking=detail     # Track native memory
-XX:+UnlockDiagnosticVMOptions     # Enable diagnostic options
-XX:+UnlockExperimentalVMOptions   # Enable experimental options
```

### Container Support (Docker/K8s)

```bash
-XX:+UseContainerSupport           # Respect cgroup limits (default Java 10+)
-XX:MaxRAMPercentage=75.0          # Use 75% of container memory for heap
-XX:InitialRAMPercentage=50.0      # Initial heap = 50% of container memory
-XX:MinRAMPercentage=25.0          # Min heap for small containers
```

---

## 15. JVM in Containers (Docker/Kubernetes)

### The Problem (Pre-Java 10)

```
Container: 2GB memory limit
JVM (Java 8 early): "I see 64GB RAM on the host!"
                     Sets -Xmx to 16GB (1/4 of host)
                     Container gets OOM-killed!
```

### The Fix

```bash
# Java 10+: Container support is ON by default
# JVM correctly reads cgroup memory limits

# Recommended container JVM flags:
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-XX:+UseG1GC", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-jar", "app.jar"]
```

### Why Not 100% of Container Memory?

```
Container: 2GB
├── JVM Heap:        75% = 1.5GB  (-XX:MaxRAMPercentage=75)
├── Metaspace:       ~100-200MB
├── Thread stacks:   ~50-200MB (200 threads × 1MB)
├── Code cache:      ~50MB
├── Direct buffers:  ~50MB
├── Native memory:   ~100MB
└── OS overhead:     ~100MB

Total ≈ 2GB → fits in container!
If heap = 100% → other areas cause OOM kill
```

---

## 16. Monitoring & Profiling Tools

### Command Line Tools

```bash
# JVM process list
jps -lv                           # List Java processes with args

# Thread dump (deadlock detection, stuck threads)
jstack <pid>                       # Print thread dump
jstack -l <pid>                    # Include lock info

# Heap summary
jmap -heap <pid>                   # Heap configuration and usage
jmap -histo <pid>                  # Object histogram (count, size)
jmap -dump:live,format=b,file=heap.hprof <pid>  # Heap dump

# JVM statistics (GC, class loading, compilation)
jstat -gc <pid> 1000               # GC stats every 1 second
jstat -gcutil <pid> 1000           # GC utilization %
jstat -class <pid>                 # Class loading stats

# JVM info
jinfo <pid>                        # JVM configuration info
jinfo -flag MaxHeapSize <pid>      # Specific flag value
```

### GUI Tools

| Tool | What It Does |
|------|-------------|
| **VisualVM** | All-in-one: monitor, profiler, thread dump |
| **JConsole** | Built-in JMX monitoring |
| **Eclipse MAT** | Heap dump analysis (find memory leaks) |
| **JFR (Flight Recorder)** | Low-overhead production profiling |
| **async-profiler** | CPU + allocation profiling (low overhead) |
| **Grafana + Prometheus** | Production monitoring with JMX/Micrometer metrics |

### Java Flight Recorder (JFR)

```bash
# Start recording
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr -jar app.jar

# Or attach to running process
jcmd <pid> JFR.start duration=60s filename=recording.jfr

# Analyze with JDK Mission Control (JMC)
```

---

## 17. Troubleshooting — OOM, CPU Spikes, GC Pauses

### OutOfMemoryError — Decision Tree

```
OOM: Java heap space
  → Heap is full. Check for memory leaks.
  → Take heap dump: -XX:+HeapDumpOnOutOfMemoryError
  → Analyze with Eclipse MAT
  → Common causes: unbounded caches, large result sets, event listeners not removed

OOM: Metaspace
  → Too many classes loaded (hot deploy, dynamic proxies)
  → Increase: -XX:MaxMetaspaceSize=512m
  → Find leak: -verbose:class to log class loading/unloading

OOM: unable to create new native thread
  → Too many threads. Check thread count: jstack <pid> | grep -c "nid="
  → Reduce thread pool sizes
  → Increase OS limits: ulimit -u

OOM: Direct buffer memory
  → NIO direct buffers exhausted
  → Increase: -XX:MaxDirectMemorySize=1g
  → Check for unclosed channels/buffers
```

### High CPU — Investigation

```bash
# Step 1: Find the Java process
top -c  # or: ps aux | grep java

# Step 2: Find the hot thread
top -H -p <pid>    # Show threads, note the high-CPU thread ID (e.g., 12345)

# Step 3: Convert to hex
printf "%x\n" 12345   # → 3039

# Step 4: Find it in thread dump
jstack <pid> | grep -A 30 "nid=0x3039"
# Shows: the stack trace of the CPU-hogging thread
```

### GC Pause Investigation

```bash
# Enable GC logging
-Xlog:gc*:file=gc.log:time,level,tags

# What to look for:
# - Pause time: "Pause Young" duration
# - Full GC: Should rarely happen
# - Allocation rate: How fast objects are created
# - Promotion rate: How fast objects move to Old Gen

# Tools:
# - GCViewer: Visualize GC logs
# - GCEasy.io: Upload GC log for analysis
```

---

## 18. GraalVM and Native Image

### What is GraalVM?

GraalVM is a JVM that adds:
1. **Graal JIT compiler** — Advanced optimizations
2. **Native Image** — Compile Java to standalone binary (no JVM needed!)
3. **Polyglot** — Run JavaScript, Python, Ruby, R on JVM

### Native Image

```bash
# Compile to native binary
native-image -jar myapp.jar

# Result: standalone executable
# Startup: ~50ms (vs ~2-5 seconds for JVM)
# Memory: ~50MB (vs ~200-500MB for JVM)
# NO JVM needed at runtime

# Trade-offs:
# ✅ Instant startup → perfect for serverless/containers
# ✅ Low memory footprint
# ❌ No JIT optimization (ahead-of-time compilation)
# ❌ Limited reflection support (needs configuration)
# ❌ Longer build time
```

### Spring Boot Native

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```
```bash
mvn -Pnative native:compile
```

---

## 19. JVM Performance Tuning Checklist

```
□ Set -Xms = -Xmx (avoid heap resize overhead)
□ Choose appropriate GC (G1 default, ZGC for low latency)
□ Set -XX:MaxGCPauseMillis for G1
□ Enable container support (-XX:MaxRAMPercentage=75)
□ Enable heap dump on OOM
□ Enable GC logging
□ Monitor GC metrics (pause time, frequency, throughput)
□ Profile before optimizing (async-profiler, JFR)
□ Check for memory leaks (Eclipse MAT)
□ Right-size thread pools
□ Use StringBuilder for string concatenation in loops
□ Avoid creating objects in hot loops
□ Use primitive collections for performance-critical code
□ Consider JFR for production profiling
```

---

## 20. Interview Q&A — 30 Questions

| # | Question | Answer |
|---|----------|--------|
| 1 | JVM memory areas? | Heap, Stack, Metaspace, PC Register, Native Stack |
| 2 | Heap vs Stack? | Heap = shared, objects. Stack = per-thread, method frames, local vars |
| 3 | What is GC? How does it work? | Finds unreachable objects from GC roots, reclaims memory |
| 4 | What are GC roots? | Local vars, static fields, active threads, JNI refs |
| 5 | Young Gen vs Old Gen? | Young = new objects, Minor GC. Old = promoted, Major GC |
| 6 | What is Eden, S0, S1? | Eden = allocation area. S0/S1 = survivor spaces (alternate each GC) |
| 7 | Object promotion? | After surviving N Minor GCs, object moves from Survivor to Old Gen |
| 8 | G1 vs ZGC? | G1 = region-based, ~200ms pauses. ZGC = colored pointers, <1ms pauses |
| 9 | What is Stop-The-World? | GC pauses ALL app threads. Shorter pauses = better user experience |
| 10 | How to diagnose OOM? | HeapDump + Eclipse MAT. Check GC logs. Identify leaking objects. |
| 11 | PermGen vs Metaspace? | PermGen (Java ≤7) = fixed heap area. Metaspace (8+) = native, auto-grows |
| 12 | What is JIT? | Compiles frequently-called bytecode to native code at runtime |
| 13 | What is escape analysis? | JIT optimization: if object doesn't escape, allocate on stack (no GC) |
| 14 | Class loading order? | Bootstrap → Platform → Application. Parent-delegation model. |
| 15 | What happens when you load a class? | Load → Verify → Prepare → Resolve → Initialize |
| 16 | What is TLAB? | Thread-Local Allocation Buffer. Per-thread Eden chunk for fast allocation |
| 17 | What is a humongous object in G1? | Object > 50% region size. Allocated in contiguous regions. |
| 18 | How JVM works in containers? | Java 10+: reads cgroup limits. Use MaxRAMPercentage for heap. |
| 19 | String pool location? | Heap (Java 7+). Was PermGen before. |
| 20 | What is Native Memory Tracking? | JVM feature to track off-heap memory usage (-XX:NativeMemoryTracking) |
| 21 | How to take a thread dump? | jstack, kill -3, JMX, JFR |
| 22 | How to detect deadlocks? | jstack shows "Found one Java-level deadlock" |
| 23 | JFR vs JMX? | JFR = continuous recording, low overhead. JMX = real-time monitoring. |
| 24 | GraalVM native image? | AOT compilation → standalone binary. Fast startup, low memory. No JIT. |
| 25 | What is tiered compilation? | C1 first (fast compile), C2 for hot methods (optimized compile) |
| 26 | What is code cache? | JVM area storing JIT-compiled native code |
| 27 | Java 8 vs 17 vs 21 JVM improvements? | 8: G1 default. 17: ZGC prod. 21: Virtual threads, Generational ZGC |
| 28 | What is -Xmx vs -XX:MaxRAMPercentage? | -Xmx = fixed size. MaxRAMPercentage = % of container memory |
| 29 | Memory leak in Java? | Objects still referenced but never used (growing caches, listeners) |
| 30 | How to tune GC? | Start with defaults, measure with GC logs, tune MaxGCPauseMillis, profile |

---

> **Pro Tip for Interviews:** Don't just memorize GC names. Explain **when** you'd use each: "For our trading platform, I'd use ZGC because sub-millisecond pauses are critical. For batch processing, Parallel GC gives us maximum throughput."

