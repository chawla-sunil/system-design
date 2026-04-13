# Garbage Collection - Interview Cheat Sheet

## Quick Summary

Garbage Collection automatically frees memory no longer needed by programs. Two fundamental approaches exist: **reference counting** (deterministic but can't handle cycles) and **tracing** (handles cycles but requires pauses). Modern collectors like Java's G1GC and ZGC, Go's concurrent GC, and Python's hybrid approach are all variations on 30+ years of research, fundamentally tracing from roots and reclaiming unreachable objects.

---

## Core Concepts You MUST Know

### 1. Root Set
**Definition:** References the runtime knows are live WITHOUT tracing.

**Includes:**
- Local variables and function arguments (stack frames)
- Global and static variables
- CPU registers (JIT-compiled code)
- Class loaders, JNI handles, interned strings (Java)
- All goroutine stacks (Go)

**Key Insight:** Everything else must earn its survival by being reachable from a root.

---

### 2. Two Fundamental Approaches

#### Reference Counting
```
Every object has a refcount.
When refcount = 0, object is freed immediately.
```

**Pros:** Deterministic destruction, no pauses
**Cons:** Can't handle cycles, expensive per-mutation overhead

#### Tracing (Mark-and-Sweep)
```
Mark:  Start from roots, traverse graph, mark reachable
Sweep: Free all unmarked objects
```

**Pros:** Handles cycles, no per-mutation cost
**Cons:** Needs stop-the-world pause (unless made concurrent)

**Winner for Servers:** Tracing (can be made concurrent, reference counting per-mutation cost scales poorly)

---

### 3. Three Classic Tracing Variants

| Algorithm | How It Works | Pro | Con |
|-----------|-------------|-----|-----|
| **Mark-Sweep** | Mark reachable, sweep free unreachable | Simple | Fragmentation |
| **Copying** | Split heap in half, copy live to other half | Zero fragmentation | 50% memory overhead |
| **Mark-Compact** | Mark, then slide live objects to one end | No fragmentation, full memory use | Multiple passes |

---

### 4. Generational Hypothesis
**Observation:** Most objects die young (temporary loop variables, request buffers) while few live forever (caches, connection pools).

**Strategy:** 
- Collect young generation frequently
- Collect old generation rarely
- Reduces work by focusing on garbage-heavy areas

**Implementation Challenge:** Write barriers track old→young pointers

---

### 5. Tricolor Marking (Enables Concurrent GC)
```
White   = Not yet visited
Grey    = Visited, children not yet scanned
Black   = Fully processed

Process: White → Grey → Black → done
Garbage: Anything still white at end
```

**Key Problem:** App modifies pointers while collector marks

**Solution:** Write barriers maintain **Tricolor Invariant:**
- Black objects must never point directly to white objects
- Violated pointer gets greyed before violation occurs

---

## Modern Collectors: Quick Reference

### Go: Concurrent Mark-and-Sweep (Non-Generational)

**Design:** Low pauses, simplicity

```
Phases:
1. Mark Start (STW): Scan stacks, turn on write barrier     ~0.044ms
2. Concurrent Mark (Running): Background workers mark        ~0.56ms
3. Mark End (STW): Drain remaining, disable barrier        ~0.13ms

Total STW typically < 1ms
```

**Barrier:** Hybrid (Dijkstra + Yuasa)
```go
shade(*slot)      // grey old referent
shade(new_ptr)    // grey new referent
*slot = new_ptr
```

**Tuning:**
- `GOGC=100` (default): GC when heap doubles
- `GOMEMLIMIT`: Container memory budget (Go 1.19+)

**Best For:** Latency-sensitive services, goroutine-heavy workloads

---

### Java G1GC: Generational Region-Based

**Design:** Pause-time target with generational benefits

**Structure:**
```
Heap = Equal-sized regions (1-32MB)
Each region: Eden, Survivor, Old, or Humongous

Young GC: Collect Eden+Survivor (short pause ~1ms)
Mixed GC: Collect young + most-garbage-dense old regions
```

**Algorithm:** SATB (Snapshot-At-The-Beginning)
- Takes logical snapshot at marking start
- Conservative: objects freed during marking survive one cycle

**Tuning:**
- `-XX:MaxGCPauseMillis=200`: Target pause time

**Best For:** 4-8GB heaps, mixed workloads, when 1-100ms pauses acceptable

---

### Java ZGC: Concurrent Sub-Millisecond

**Design:** Sub-millisecond pauses regardless of heap size

**Innovation: Colored Pointers** (encodes GC metadata in pointer bits)
```
64-bit pointer = 42 bits address + 20+ bits metadata

M0, M1: Mark bits (alternate cycles)
R:      Remap bit (tracks relocation updates)
F:      Finalizable bit
```

**Load Barrier:** Lazy pointer fixup
```
Load field:
  if (color != expected) {
    Fix pointer to new address
    Update remap bit
  }
```

**Concurrent Phases:** Mark and relocate while app runs, STW only at phase boundaries (<1ms each)

**Generational ZGC (Java 21+):** Adds young/old generations, maintains sub-ms guarantee

**Best For:** Large heaps (>8GB), latency critical, need <1ms pauses

---

### Python: Reference Counting + Cycle Detector

**Hybrid Approach:**
1. Reference counting for immediate cleanup
2. Tracing cycle detector for unreachable cycles

**Reference Counting:**
```python
x = []                    # refcount = 1
y = x                     # refcount = 2
del y                     # refcount = 1
del x                     # refcount = 0 → freed immediately
```

**Problem:** Cycles
```python
a.ref = b
b.ref = a   # Both have refcount >= 1, neither freed
```

**Solution:** Cycle detector (generational tracing GC)
```
Generation 0: ~700 allocations
Generation 1: 10 Gen0 collections
Generation 2: 10 Gen1 collections
```

**Advantages:** Deterministic `__del__` execution, resource cleanup immediate
**Best For:** Scripts, interactive apps, when immediate cleanup critical

---

## Interview Questions You'll Get

### Q1: "What's the difference between reference counting and tracing?"

**Answer:**
- **Reference Counting:** Tracks incoming references, frees immediately when count=0. Pros: no pauses. Cons: can't handle cycles, expensive per-mutation overhead.
- **Tracing:** Starts from roots, marks reachable, sweeps rest. Pros: handles cycles, no per-mutation cost. Cons: needs pause (unless concurrent).
- **For servers:** Tracing wins because per-mutation cost scales poorly.

---

### Q2: "How does concurrent GC avoid corrupting the heap?"

**Answer:**
Tricolor invariant: Black objects never point directly to white objects.

Write barrier enforces this:
```
When writing pointer to object:
1. Grey the new referent (Dijkstra)
2. Grey the old referent (Yuasa)

If violation would occur, barrier prevents it.
```

---

### Q3: "Why is generational GC important?"

**Answer:**
Generational hypothesis: most objects die young. By collecting young frequently and old rarely, we:
- Do most work on garbage-dense areas
- Reduce overall pause time and CPU overhead
- Found everywhere (Java, Python) except Go (which chose simplicity)

---

### Q4: "Tell me about Go's write barrier"

**Answer:**
Go 1.8 introduced hybrid barrier:

```go
shade(*slot)      // old referent (Yuasa)
shade(new_ptr)    // new referent (Dijkstra)
*slot = new_ptr
```

**Why both?**
- Dijkstra prevents "black→white" violations for new refs
- Yuasa prevents losing references when old ones overwritten
- Combined: GC doesn't need expensive stack re-scan at end

Cost: Every heap pointer write pays barrier overhead during mark phase.
Benefit: STW drops from 10-100ms to <1ms.

---

### Q5: "How does ZGC achieve sub-millisecond pauses?"

**Answer:**
Three innovations:
1. **Colored pointers:** Metadata encoded in pointer bits
2. **Load barriers:** App fixes stale pointers lazily on access
3. **Concurrent relocation:** Objects moved while app runs, pointers updated on first load

**Tradeoff:** Every pointer load pays cost of barrier check (fast path). In exchange, no STW relocation pause.

---

### Q6: "When would you choose G1GC vs ZGC?"

**Answer:**

| Criterion | G1GC | ZGC |
|-----------|------|-----|
| Heap size | < 8GB | > 8GB |
| Pause time tolerance | 1-200ms | < 1ms required |
| CPU overhead | Lower | Higher (load barriers) |
| Memory overhead | Moderate | Higher (colored ptrs) |
| Typical case | Default for web apps | Latency-critical |

---

### Q7: "What's a 'stop-the-world' pause and why is everyone trying to avoid it?"

**Answer:**
Stop-the-world = entire application freezes while GC runs.

**Why bad:**
- P99 latency spike
- Affects user experience immediately
- At high frequency (100+ ms pause every second), unacceptable for real-time systems

**Modern solution:** Concurrent GC
- Mark and sweep while app runs
- Only brief STW (scan stacks, toggle barriers): <1ms
- Tricolor marking makes this safe

---

### Q8: "Explain Python's cycle detector briefly"

**Answer:**
CPython uses reference counting primarily (immediate destruction), but reference counting can't handle cycles:

```python
a.ref = b
b.ref = a   # Both refcount >= 1 despite being unreachable
```

Solution: Separate tracing GC (cycle detector) that:
1. Tracks container objects (lists, dicts, instances)
2. Runs generationally (3 generations)
3. Detects which objects are only kept alive by cycles
4. Breaks cycles and frees them

**Why Python chose this:** Deterministic `__del__` execution matters for resource cleanup (file closes, database connections).

---

## Things to Practice

### Write a Simple Mark-and-Sweep in Pseudocode

```python
def mark(root):
    """Mark all reachable objects"""
    stack = [root]
    while stack:
        obj = stack.pop()
        if obj.marked:
            continue
        obj.marked = True
        for child in obj.children:
            if not child.marked:
                stack.append(child)

def sweep(heap):
    """Free unmarked objects"""
    alive = []
    for obj in heap:
        if obj.marked:
            obj.marked = False  # reset for next cycle
            alive.append(obj)
        else:
            free(obj)
    return alive

def gc(heap, roots):
    for root in roots:
        mark(root)
    return sweep(heap)
```

### Understand This GC Log Line

```
gc 1 @0.011s 1%: 0.044+0.56+0.13 ms clock, 0.62+0.21/0.57/0+1.8 ms cpu

0.044ms = STW mark start (wall clock)
0.56ms  = Concurrent mark (application running)
0.13ms  = STW mark end (wall clock)

Total STW: 0.174ms
Total time: 0.73ms
```

---

## Key Papers (For Deep Dives)

1. **McCarthy (1960):** "Recursive Functions of Symbolic Expressions..."
   - Introduced mark-and-sweep
   
2. **Wilson (1992):** "Uniprocessor Garbage Collection Techniques"
   - Taxonomy organizing all GC research
   - Everything modern builds on this

---

## Red Flags in Interview Answers

❌ "GC always pauses the program"
✅ "Modern GCs can run concurrently, with only brief STW pauses"

❌ "Reference counting is always better"
✅ "Reference counting has per-mutation overhead; tracing better for servers"

❌ "More frequent GC is always good"
✅ "Tradeoff between pause frequency and max pause time"

❌ "All heaps should use generational collection"
✅ "Generational adds complexity; Go chose not to use it"

---

## Talking Points for Different Interview Scenarios

### "Tell me about garbage collection"

Start with: "GC automatically manages memory by determining what's reachable and freeing the rest."

1. Two approaches: reference counting vs tracing
2. Tracing dominates servers because reference counting scales poorly
3. Modern collectors use concurrent marking to avoid pauses
4. Different tradeoffs: Go emphasizes simplicity, ZGC emphasizes latency, G1GC balances both
5. Everything traces back to McCarthy's 1960 mark-and-sweep

### "We're having GC pause spikes, how would you debug?"

1. Enable GC logging (GODEBUG, -Xlog:gc)
2. Look for patterns: frequency, duration, heap size
3. Check if full GC happening (very bad)
4. For Java: might need ZGC if > 8GB and latency critical
5. For Go: tune GOGC or GOMEMLIMIT
6. Profile heap: are objects surviving longer than expected?

### "Should we use reference counting in our system?"

1. Reference counting good for: immediate cleanup, deterministic destruction (Python use case)
2. Reference counting bad for: high allocation rates, multithreaded workloads, cycles
3. For servers: use tracing
4. For resource-heavy systems: might need deterministic cleanup (hybrid approach like Python)

---

## Quick Reference: When Each Collector Shines

| Collector | Best For |
|-----------|----------|
| **CPython** | Scripts, quick development, deterministic cleanup |
| **Go GC** | Microservices, goroutine-heavy, good default |
| **G1GC** | 4-8GB heaps, accept 1-100ms pauses, Java ecosystem |
| **ZGC** | >8GB heaps, latency critical, sub-1ms requirements |
| **Generational** | High allocation rate, long-lived objects |

---

## The Bottom Line

**What's the fundamental problem?**
Determine which objects are live and reclaim the rest.

**How do we know what's live?**
Start from roots (stack, registers, globals) and trace reachable objects.

**How do we do this without stopping the world?**
Concurrent marking with tricolor abstraction + write barriers to maintain safety.

**What are the tradeoffs?**
- **Pause time:** Concurrent GC reduces pauses but needs write barriers (CPU overhead)
- **Memory:** Copying collectors need 2x space; colored pointers add overhead
- **Simplicity:** Generational GC is complex but cuts pause time; Go chose not to use it

Master these concepts and you'll ace GC interview questions.

