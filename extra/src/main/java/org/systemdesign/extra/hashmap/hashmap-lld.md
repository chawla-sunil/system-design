# LLD: HashMap Internal Implementation in Java

## 🎤 Interview Approach

### Step 1: Clarify Requirements
- Generic `HashMap<K, V>` with `put`, `get`, `remove`, `containsKey`, `size`
- Collision handling via **separate chaining** (linked list per bucket)
- Dynamic resizing when load factor exceeds threshold
- Null key support

### Step 2: Core Concept

```
Bucket Array (Entry<K,V>[]):

  Index 0  → [K=null, V=999] → null
  Index 1  → null
  Index 2  → [K="apple", V=1] → [K="grape", V=5] → null   ← collision chain
  Index 3  → [K="banana", V=2] → null
  ...
```

**How it works:**
1. `hashCode()` of key → spread bits → `& (capacity - 1)` → bucket index
2. Walk the linked list at that bucket using `equals()` to find exact key
3. Insert at head if not found; update value if found

### Step 3: Key Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Collision strategy | Separate chaining (linked list) | Simple, effective for moderate load |
| Initial capacity | 16 | Power of 2 enables bitwise index (`hash & (cap-1)`) |
| Load factor | 0.75 | Balance between space and time |
| Resize strategy | 2× capacity + rehash all | Keeps capacity as power of 2 |
| Hash spreading | `h ^ (h >>> 16)` | Mixes high bits into low bits to reduce clustering |
| Null key | Stored at bucket 0 (hash=0) | Same as Java's HashMap |

### Step 4: Class Diagram

```
MyHashMap<K, V>
├── Entry<K, V>[]  buckets        // array of bucket heads
├── int             size           // number of entries
├── float           loadFactor     // threshold for resize
│
├── put(K, V): V
├── get(K): V
├── remove(K): V
├── containsKey(K): boolean
├── size(): int
├── isEmpty(): boolean
│
├── hash(K): int              // private: compute spread hash
├── bucketIndex(int): int     // private: hash → array index
├── resize(): void            // private: double capacity & rehash
└── keyEquals(K, K): boolean  // private: null-safe equals

Entry<K, V> (static inner class)
├── K           key
├── V           value
├── int         hash      // cached hash
└── Entry<K,V>  next      // next in chain
```

### Step 5: Complexity

| Operation | Average | Worst (all collisions) |
|---|---|---|
| `put` | O(1) | O(n) |
| `get` | O(1) | O(n) |
| `remove` | O(1) | O(n) |
| `resize` | O(n) | O(n) |

### Step 6: Interview Follow-ups I'd Mention

1. **Java 8 Treeification**: When a bucket chain exceeds 8 nodes, Java converts it to a red-black tree → worst case becomes O(log n) per bucket.

2. **Why power of 2 capacity?**: `hash & (capacity - 1)` is a fast modulo that only works when capacity is a power of 2.

3. **Why cache the hash in Entry?**: Avoids recomputing `hashCode()` during resize (rehashing millions of entries).

4. **Thread safety**: This implementation is NOT thread-safe. For concurrency use `ConcurrentHashMap` which uses lock striping (segment-level locking in Java 7, node-level CAS + synchronized in Java 8+).

5. **Immutable keys**: Keys should be immutable (or at least have stable `hashCode`/`equals`). If a key's hash changes after insertion, the entry becomes unreachable.

## 📁 Files

- [`MyHashMap.java`](MyHashMap.java) — Core implementation
- [`MyHashMapDemo.java`](MyHashMapDemo.java) — Driver with collision & resize tests

