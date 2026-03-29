# ⚡ JVM Internals in 5 Minutes — Interview Cheat Sheet

> Quick-fire JVM concepts. Know these cold — every senior Java interview asks them.

---

## JVM Architecture — The Big Picture

```
┌──────────────────────────────────────────────────────────┐
│                        JVM                                │
│                                                          │
│  ┌────────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │  Class Loader  │  │  Runtime     │  │  Execution  │  │
│  │  Subsystem     │  │  Data Areas  │  │  Engine     │  │
│  │                │  │  (Memory)    │  │             │  │
│  │ Bootstrap      │  │ Heap         │  │ Interpreter │  │
│  │ Extension      │  │ Stack        │  │ JIT Compiler│  │
│  │ Application    │  │ Method Area  │  │ GC          │  │
│  └────────────────┘  │ PC Register  │  └─────────────┘  │
│                      │ Native Stack │                    │
│                      └──────────────┘                    │
└──────────────────────────────────────────────────────────┘
```

---

## JVM Memory Model — Where Things Live

```
┌─────────────────────────────────────────┐
│               HEAP (shared)              │  ← Objects live here
│  ┌──────────────────┬──────────────┐    │
│  │   Young Gen      │   Old Gen    │    │
│  │ ┌─────┬────┬────┐│              │    │
│  │ │Eden │ S0 │ S1 ││  (Tenured)  │    │
│  │ └─────┴────┴────┘│              │    │
│  └──────────────────┴──────────────┘    │
├─────────────────────────────────────────┤
│           METASPACE (off-heap)           │  ← Class metadata (Java 8+)
├─────────────────────────────────────────┤
│      STACK (per thread)                  │  ← Method frames, local vars
├─────────────────────────────────────────┤
│      PC Register (per thread)            │  ← Current instruction pointer
├─────────────────────────────────────────┤
│      Native Method Stack (per thread)    │  ← For JNI calls
└─────────────────────────────────────────┘
```

---

## Memory Areas — What Goes Where

| Area | Shared? | What's Stored | Error on Overflow |
|------|---------|--------------|-------------------|
| **Heap** | ✅ All threads | Objects, arrays | `OutOfMemoryError: Java heap space` |
| **Stack** | ❌ Per thread | Local vars, method frames | `StackOverflowError` |
| **Metaspace** | ✅ All threads | Class metadata, method bytecode | `OutOfMemoryError: Metaspace` |
| **PC Register** | ❌ Per thread | Address of current instruction | — |
| **Native Stack** | ❌ Per thread | Native method calls (JNI) | `StackOverflowError` |

---

## Class Loading — 3 Loaders

```
Bootstrap ClassLoader      ← Loads rt.jar (java.lang.*, java.util.*)
       ▲
Extension ClassLoader      ← Loads jre/lib/ext
       ▲
Application ClassLoader    ← Loads your classes (classpath)
       ▲
Custom ClassLoaders         ← Tomcat, OSGi, etc.
```

**Delegation Model:** Child asks parent first. If parent can't load → child tries.

**Interview Q:** *"What is class loading?"*  
**A:** Loading → Linking (Verify → Prepare → Resolve) → Initialization

---

## Garbage Collection — The Interview Favorite

### How GC Works (Generational)

```
1. New object → Eden
2. Eden fills up → Minor GC
3. Surviving objects → S0 (Survivor 0)
4. Next Minor GC → Survivors from Eden + S0 → S1
5. After N survivals → Promoted to Old Gen
6. Old Gen fills up → Major GC (Full GC) ← STOP THE WORLD (STW)!
```

### GC Algorithms

| GC | Best For | STW Pauses | JVM Flag |
|----|----------|-----------|----------|
| **Serial** | Small apps, single-core | Long | `-XX:+UseSerialGC` |
| **Parallel** | Throughput (batch jobs) | Medium | `-XX:+UseParallelGC` |
| **G1** | Balanced (default Java 9+) | Short | `-XX:+UseG1GC` |
| **ZGC** | Ultra-low latency (<1ms) | < 1ms | `-XX:+UseZGC` |
| **Shenandoah** | Low latency | < 10ms | `-XX:+UseShenandoahGC` |

**Interview Q:** *"Which GC would you use?"*  
**A:** G1 (default) for most apps. ZGC for latency-sensitive (trading, real-time). Parallel for throughput-focused batch.

---

## JIT Compilation

```
Java Source → javac → Bytecode (.class)
                         │
                    JVM runs it
                         │
                    ┌────┴────┐
                    │Interpreter│  ← Slow, but starts fast
                    └────┬────┘
                         │
              Method called many times (hot method)
                         │
                    ┌────┴────┐
                    │   JIT   │  ← Compiles to native code
                    │Compiler │
                    └────┬────┘
                         │
                   Native Code   ← Fast! Runs at near-C speed
```

**Two JIT Compilers:**
- **C1 (Client):** Quick compilation, less optimization
- **C2 (Server):** Slower compilation, maximum optimization
- **Tiered Compilation (default):** C1 first → C2 for hot methods

---

## Important JVM Flags

```bash
# Memory
-Xms512m              # Initial heap size
-Xmx2g                # Max heap size
-XX:MetaspaceSize=256m # Initial metaspace
-Xss512k              # Thread stack size

# GC
-XX:+UseG1GC          # Use G1 collector
-XX:+UseZGC           # Use ZGC (Java 15+)
-XX:MaxGCPauseMillis=200  # Target GC pause time

# Container Support
-XX:+UseContainerSupport    # Respect container limits (default Java 10+)
-XX:MaxRAMPercentage=75.0   # Use 75% of container memory for heap

# Debugging
-XX:+HeapDumpOnOutOfMemoryError  # Dump heap on OOM
-XX:HeapDumpPath=/tmp/dump.hprof # Dump location
-verbose:gc                      # GC logging
-Xlog:gc*                        # Detailed GC logging (Java 9+)
```

---

## String Pool

```java
String s1 = "hello";          // Goes to String Pool (Heap)
String s2 = "hello";          // Same reference from pool
String s3 = new String("hello"); // New object on heap (NOT pooled)

s1 == s2     // true  (same reference)
s1 == s3     // false (different objects)
s1.equals(s3) // true (same content)

s3.intern()  // Returns the pooled reference
```

---

## 🔥 Top 10 Interview Questions (Quick Answers)

| # | Question | Key Answer |
|---|----------|-----------|
| 1 | JVM memory areas? | Heap (objects), Stack (per-thread frames), Metaspace (class metadata) |
| 2 | Heap vs Stack? | Heap = shared, objects, GC. Stack = per-thread, local vars, auto-cleanup. |
| 3 | What is GC? | Automatic memory management. Finds and reclaims unreachable objects. |
| 4 | Young Gen vs Old Gen? | Young = new objects, frequent Minor GC. Old = long-lived, Major GC. |
| 5 | What is Stop-The-World? | GC pauses ALL application threads to do its work. |
| 6 | G1 vs ZGC? | G1 = balanced (default). ZGC = sub-millisecond pauses for low latency. |
| 7 | What is JIT? | Compiles hot bytecode to native machine code at runtime for performance. |
| 8 | Class loading order? | Bootstrap → Extension → Application. Parent delegation model. |
| 9 | PermGen vs Metaspace? | PermGen (Java 7-) = fixed heap. Metaspace (Java 8+) = native memory, auto-grows. |
| 10 | How to diagnose OOM? | `-XX:+HeapDumpOnOutOfMemoryError`, analyze with MAT/VisualVM, check GC logs. |

---

## Quick Reference

```
Heap       = Where objects live (shared, GC-managed)
Stack      = Where method frames live (per-thread)
Metaspace  = Where class metadata lives (off-heap, Java 8+)
GC         = Reclaims unreachable heap objects
JIT        = Compiles hot methods to native code
ClassLoader = Loads .class files into JVM
```

