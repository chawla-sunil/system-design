# Garbage Collection: From First Principles to Modern Collectors

## Introduction

Garbage Collection (GC) is a critical concern in managed languages like Java, Go, and Python. This comprehensive guide traces GC from its theoretical foundations in McCarthy's 1960 Lisp paper through modern implementations. Understanding GC mechanics is essential for optimizing latency-sensitive systems and understanding performance tradeoffs.

**Why This Matters:**
- GC pauses directly impact service latency and user experience
- Different collectors make fundamental tradeoffs: pause time vs. throughput vs. memory overhead
- Modern systems require sub-millisecond or even sub-100μs pause times

---

## Part 1: Historical Foundations

### McCarthy (1960): The Birth of Mark-and-Sweep

McCarthy's 1960 paper introduced Lisp, and almost incidentally, the first garbage collector. He needed a way to automatically manage memory for symbolic expressions without burdening programmers.

**The Algorithm:**
1. **Mark Phase:** Start from root variables and traverse the object graph, marking all reachable objects
2. **Sweep Phase:** Scan all memory and free unmarked objects

**Advantages:**
- Handles cycles naturally (unreachable cycles never get marked)
- No per-object bookkeeping
- Programmer-friendly

**Disadvantage:**
- Stop-the-world: The entire program freezes while collection runs
- Acceptable in 1960, but problematic for modern high-frequency systems handling thousands of requests per second

### Wilson (1992): The Definitive Taxonomy

Paul Wilson's "Uniprocessor Garbage Collection Techniques" organized 30+ years of GC research into a coherent framework. Everything modern collectors do today is a variation on Wilson's categories.

**Wilson's Three Core Algorithms:**

#### 1. Mark-and-Sweep (Tracing)
```
Mark:   Traverse from roots, mark reachable objects
Sweep:  Free all unmarked objects
```
- **Pro:** Simple, handles cycles
- **Con:** Heap fragmentation after multiple cycles

#### 2. Copying (Semi-Space)
```
Heap divided into two halves:
- From-space: Where allocation happens
- To-space: Copy destination

When from-space fills:
1. Copy all live objects to to-space
2. Update all pointers
3. Swap roles, discard old from-space
```
- **Pro:** No fragmentation, fast allocation (bump pointer)
- **Con:** Only half the heap is usable at any time (50% memory overhead)

#### 3. Reference Counting
```
Every object maintains a count of references pointing to it.
When count reaches 0, object is freed immediately.
```
- **Pro:** Deterministic destruction, no pause times
- **Con:** Cannot handle cycles, per-mutation overhead on pointer writes

**Two Influential Observations:**

1. **Generational Hypothesis:** Most objects die young
   - Temporary objects (request-scoped buffers, loop variables) become garbage quickly
   - Long-lived objects live for the entire program lifetime
   - Collect young objects frequently, old objects rarely

2. **Tricolor Marking:** Enables concurrent collection
   - White: Not yet visited
   - Grey: Visited, but children not yet scanned
   - Black: Fully processed
   - This abstraction makes concurrent marking safe

---

## Part 2: Two Fundamental Approaches

### Reference Counting

Each object maintains a count of incoming references:

```
Object A (refcount: 2) ← pointers from B and C
Delete reference from C → Object A (refcount: 1)  # Still alive
Delete reference from B → Object A (refcount: 0)  # Freed immediately
```

**Advantages:**
- Deterministic destruction (files close, resources released immediately)
- No pause times
- No need to stop the world

**Disadvantages:**

1. **Cycle Problem:** Objects pointing to each other maintain non-zero counts even when unreachable
   ```
   A → B (both have refcount ≥ 1)
   B → A (both have refcount ≥ 1)
   Neither freed by refcounting alone
   ```

2. **Per-Mutation Overhead:** Every pointer assignment requires atomic operations in multithreaded code
   - Expensive at high allocation rates

**Used by:** CPython, Swift, Rust (Arc)

### Tracing (Mark-and-Sweep)

The collector starts from known-live references (roots) and traces the entire object graph.

**Root Set Definition:**
Objects the runtime knows are live without tracing:
- Local variables and function arguments in active stack frames
- Global and static variables
- CPU registers (for JIT-compiled code)
- Class loaders, interned strings, JNI handles (Java-specific)
- All goroutine stacks (Go-specific)

**Example:**
```
Stack: main() { conn, config, request, response }
       handleRequest() { ...more variables... }

All objects reachable from these stack variables are alive.
Everything else on the heap is garbage.
```

**Advantages:**
- Handles cycles naturally (unreachable cycles never visited)
- No per-mutation overhead

**Disadvantages:**
- Traditional approach requires stop-the-world pause
- After fragmentation, finding space becomes costly

**Used by:** Java, Go (both use tracing-based collectors)

**Why Tracing Dominates:**
Modern servers perform thousands of allocations per second across multiple threads. Reference counting's per-mutation cost (atomic operations) becomes measurable. Tracing collectors can be made concurrent with only brief pauses, making them superior for server workloads.

---

## Part 3: Tracing Variants

### Mark-Sweep (Simplest Form)

```
1. Mark all reachable objects starting from roots
2. Sweep: free unmarked, add to free list
```

**Issue:** Fragmentation
- Heap becomes fragmented like Swiss cheese
- May have 100MB free but no single contiguous block large enough for allocation

### Copying/Semi-Space

```
From-space: [A*][gap][B*][gap][C*]
To-space:   [empty...]

After collection:
From-space: [freed entirely]
To-space:   [A*][B*][C*][free...]
```

**Benefits:**
- Zero fragmentation
- Fast allocation (bump pointer)

**Cost:**
- 50% memory overhead (half heap always empty)

### Mark-Compact

```
Before: [A*][__][B*][__][__][C*][__][D*]
After:  [A*][B*][C*][D*][__][__][__][__]
```

- Eliminates fragmentation without 50% memory cost
- Cost: Multiple passes over heap (mark → compute addresses → update pointers → move)

---

## Part 4: Modern Collectors in Practice

### Go: Tri-Color Concurrent Mark-and-Sweep

**Design Philosophy:** Low pause times, no generational collection, no compaction

**Tri-Color Abstraction:**
```
White (not yet visited) → Grey (visited) → Black (fully processed)

Start:     All white, roots grey
Step 1:    Pick grey object, mark children grey, mark object black
Step 2:    Repeat until no grey remain
Result:    All white objects are garbage
```

**Correctness Challenge:** Concurrent Marking

The hard part: Application keeps running and modifying pointers while collector marks.

```
Tricolor Invariant: Black objects must never point to white objects
(Otherwise white objects become invisible and get incorrectly freed)
```

**Go's Solution: Hybrid Write Barrier (Go 1.8)**

Combines Dijkstra's insertion barrier + Yuasa's deletion barrier:

```go
// On heap pointer writes:
shade(*slot)      // Grey the old referent (Yuasa)
shade(new_ptr)    // Grey the new referent (Dijkstra)
*slot = new_ptr
```

- Allows GC to skip expensive stack re-scans at end of marking
- Reduces STW pause from 10-100ms to <1ms

**Key Features:**
- Non-generational (simple, but collects more frequently)
- Non-compacting (uses tcmalloc-style allocator with size classes)
- Mostly concurrent (only brief STW for stack scan)

**Tuning:**
- `GOGC` (default 100): Controls heap growth before next GC
  - `GOGC=100`: GC when heap doubles
  - `GOGC=50`: More aggressive collection
  - `GOGC=200`: Less aggressive

- `GOMEMLIMIT` (Go 1.19+): Soft memory limit for container environments

**GC Trace Example:**
```
gc 1 @0.011s 1%: 0.044+0.56+0.13 ms clock

0.044ms - STW mark start (wall clock)
0.56ms  - Concurrent mark (application running)
0.13ms  - STW mark end (wall clock)

Total STW: 0.044 + 0.13 = 0.174ms
Total time including concurrent: 0.73ms
```

---

### Java: G1GC (Garbage First Collector)

Default since JDK 9. Generational, region-based, incremental.

**Design:** Minimize pause times while maintaining good throughput

**Region-Based Approach:**
```
Heap divided into equal regions (1MB-32MB):
+-----+-----+-----+-----+-----+-----+
| Eden| Eden| Surv| Old | Old |Free |
+-----+-----+-----+-----+-----+-----+

Roles can change between collections
```

**Collection Strategy:**

1. **Young Collection (Minor GC):**
   - Mark young regions with parallel multi-threaded marker
   - Copy survivors to new survivor regions or promote to old
   - Discard old Eden regions
   - Short pause (young regions small, mostly dead)

2. **Mixed Collection:**
   - Concurrent marking determines which old regions have most garbage
   - Evacuate both young and most-profitable old regions together
   - "Garbage First" = maximize reclamation per unit pause time

**SATB (Snapshot-At-The-Beginning):**
```
At marking start: Take logical snapshot of live objects
Even if app drops references during marking:
- Old reference still counted as live
- Conservative but correct

Example:
obj.field = null   (was pointing to X)
Write barrier records "X was here", marks X grey
```

**Tuning:**
- `-XX:MaxGCPauseMillis=200`: Target pause time (default 200ms)

**Pause Times:**
- Young GC: 0.6-1.0ms
- Mixed GC: 1-100ms depending on old generation size
- Full GC: Can pause for seconds (avoid!)

---

### Java: ZGC (Z Garbage Collector)

Available since Java 11, production-ready in Java 15. Target: Sub-millisecond pauses regardless of heap size.

**Innovation: Colored Pointers**

Encodes GC metadata directly in unused pointer bits:

```
64-bit pointer layout:
+---------+--+--+--+--+-----------------------+
| unused  |F |M1|M0|R | address (42 bits)    |
+---------+--+--+--+--+-----------------------+

M0, M1: Mark bits (alternate each cycle)
R:      Remap bit (tracks pointer fixup)
F:      Finalizable bit
```

**Benefits:**
- GC metadata travels with pointer
- No separate side tables needed
- Enables fast barrier checks

**Load Barriers:**

Every pointer load triggers a check:

```
Load obj.field:
  if (field.color != expected) {
    // Slow path: object was relocated
    new_addr = forwarding_table[old_addr]
    update field's address and remap bit
  }
```

**Concurrent Relocation:**

```
Object moved from 0x1000 → 0x2000
Stale pointer stays (remap bit = 0)
On next load: barrier detects, fixes lazily
Overhead: Fast path (bit check) is cheap
```

**Concurrent Phases:**
1. Concurrent marking
2. Concurrent relocation
3. Only brief STW to start/end phases (< 1ms)

**Generational ZGC (Java 21+):**
- Adds young/old generations
- Collects young frequently, old rarely
- Maintains sub-millisecond pause guarantee

**When to Use:**
| Heap Size | Latency Requirements | Recommendation |
|-----------|----------------------|----------------|
| < 8GB | Normal web service | G1GC |
| > 8GB | Latency critical | ZGC |
| Any | Sub-millisecond required | ZGC |

---

### Python: Reference Counting + Cyclic GC

**Unique Approach:** Reference counting as primary mechanism + tracing cycle detector for cycles

**Reference Counting:**

```python
import sys
x = []
print(sys.getrefcount(x))  # 2: one from x, one temporary

y = x
print(sys.getrefcount(x))  # 3: from x, from y, temporary

del y
print(sys.getrefcount(x))  # 2: back to normal
```

**Advantages:**
- Deterministic destruction: `__del__` runs immediately
- Resource cleanup (file closes) instant
- No GC pause times

**The Cycle Problem:**

```python
class Node:
    def __init__(self, name):
        self.name = name
        self.ref = None

a = Node("A")
b = Node("B")
a.ref = b
b.ref = a   # Cycle!

del a
del b
# Still alive! Refcounts: A=1 (from b), B=1 (from a)
```

**CPython's Solution: Cycle Detector**

A supplementary tracing GC that tracks container objects (lists, dicts, instances).

**Generational Cycle Detection (3 generations):**

```
Generation 0: New containers (threshold: 700 allocations)
Generation 1: Promoted from Gen0 (threshold: collected 10 times)
Generation 2: Long-lived (threshold: collected 10 times)

Effect:
- Gen0 collects every 700 allocations
- Gen1 collects every ~7,000 allocations  
- Gen2 collects every ~70,000 allocations
```

**Example: How Cycle Detector Works**

Given objects X, Y, Z where X→Y, Y→X, Y→Z, with local_var→X:

```
Step 1: Copy refcounts
  X=2 (local + Y), Y=1 (X), Z=1 (Y)

Step 2: Subtract internal references
  X→Y: X-1 = 1
  Y→X: Y-1 = 0
  Y→Z: Z-1 = 0

Step 3: Check survivors
  X=1: Something outside points to it (local_var) → ALIVE
  Y=0, Z=0: Only reachable from X → ALIVE (via X)
```

If local_var goes away (X becomes refcount=1), all become garbage.

**Key Implementation Detail:**

The GIL (Global Interpreter Lock) makes reference counting safe:
- Only one thread executes Python bytecode at a time
- No atomic operations needed for refcount updates
- Removing GIL requires expensive atomic operations everywhere

Python 3.13 experimental free-threading mode uses biased reference counting to reduce this cost.

---

## Part 5: Comparison and Tradeoffs

| Feature | Java G1GC | Java ZGC | Go GC | Python |
|---------|-----------|----------|-------|--------|
| **Family** | Tracing | Tracing | Tracing | Ref Counting + Tracing |
| **Variant** | Mark-Copy/Compact | Concurrent relocation | Mark-Sweep | Immediate + Cycle detector |
| **Generational** | Yes (young/old) | Yes (Java 21+) | No | Yes (3 gen in cycle detector) |
| **Concurrent** | Partial | Mostly | Yes | No (cycle detector STW) |
| **Compaction** | Yes | Yes (relocation) | No | No |
| **Typical STW** | 1-200ms | <1ms | <1ms | Rare, short |
| **Memory Overhead** | Moderate | Higher (colored ptrs) | Low | Low (refcount field) |
| **Primary Tuning** | MaxGCPauseMillis | Self-tuning | GOGC/GOMEMLIMIT | gc.set_threshold() |

**Key Insights:**

1. **Tracing Dominates Server Runtimes:** Reference counting's per-mutation cost is expensive at scale. Tracing collectors can be made concurrent with minimal pauses.

2. **Generational Collection is Everywhere Except Go:** Goes against generational hypothesis by collecting all ages equally. Recently experimental generational support being developed.

3. **Compaction vs. Non-Compaction:** Real design fork
   - **Compact:** Java, Python → bump-pointer allocation (fast), no fragmentation
   - **Non-Compact:** Go → simpler write barriers, no pointer updates, uses size-class allocator

4. **Colored Pointers:** ZGC's innovation resurrects Wilson's 1992 pointer-tagging idea with modern sophistication.

---

## Part 6: Practical Considerations

### How to Debug GC Issues

**Go:**
```bash
GODEBUG=gctrace=1 go run app.go
# Look for: STW times, concurrent phase duration, heap growth
```

**Java:**
```bash
java -Xlog:gc GCDemo
# Or detailed: -XX:+PrintGCDetails -XX:+PrintGCTimeStamps
```

**Python:**
```python
import gc
print(gc.get_threshold())  # Current thresholds
gc.collect()               # Force full collection
gc.disable()               # Disable if no cycles exist
```

### When Each Collector Shines

| Scenario | Best Choice | Why |
|----------|-------------|-----|
| 99-percentile latency critical | ZGC | Guaranteed <1ms pauses |
| Memory constrained | G1GC | Better compaction |
| High-throughput batch | G1GC | Can tolerate longer pauses |
| Many small objects | Go | Efficient allocation |
| Deterministic cleanup needed | Python | Reference counting |

---

## Summary

From McCarthy's 1960 mark-and-sweep to modern collectors like ZGC, the fundamental problem remains unchanged: **determine which objects are live and reclaim the rest.** All modern GC innovations are engineering refinements addressing one core question: **How do we collect garbage without stopping the world?**

The answer varies by language, workload, and requirements, but they all build on Wilson's 1992 taxonomy. Understanding the tradeoffs—pause time vs. throughput vs. memory—is essential for making informed design decisions in latency-sensitive systems.

