package org.systemdesign.extra.hashmap;

/**
 * LLD: Custom HashMap Implementation (Separate Chaining)
 *
 * Key Design Decisions:
 * 1. Array of linked-list buckets for collision handling
 * 2. Load factor 0.75 triggers resize (double capacity + rehash)
 * 3. Uses hashCode() & equals() contract for key lookup
 * 4. Supports null key (stored at bucket 0)
 */
public class MyHashMap<K, V> {

    // ─── Inner Node class (linked list entry) ───
    private static class Entry<K, V> {
        final K key;
        V value;
        final int hash; // cached hash to avoid recomputation during resize
        Entry<K, V> next;

        Entry(K key, V value, int hash, Entry<K, V> next) {
            this.key = key;
            this.value = value;
            this.hash = hash;
            this.next = next;
        }
    }

    // ─── Constants ───
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // ─── Fields ───
    private Entry<K, V>[] buckets;
    private int size;
    private final float loadFactor;

    // ─── Constructors ───
    public MyHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public MyHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked")
    public MyHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity <= 0) throw new IllegalArgumentException("Capacity must be > 0");
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) throw new IllegalArgumentException("Invalid load factor");
        this.loadFactor = loadFactor;
        this.buckets = new Entry[initialCapacity];
        this.size = 0;
    }

    // ─── Hash function ───
    // Spread bits of hashCode to reduce collisions (same as Java HashMap)
    private int hash(K key) {
        if (key == null) return 0;
        int h = key.hashCode();
        return h ^ (h >>> 16); // spread higher bits into lower bits
    }

    // Map hash → bucket index
    private int bucketIndex(int hash) {
        return hash & (buckets.length - 1); // works because capacity is power of 2
    }

    // ─── PUT ───
    public V put(K key, V value) {
        int h = hash(key);
        int index = bucketIndex(h);

        // Walk the chain — if key exists, update value
        Entry<K, V> current = buckets[index];
        while (current != null) {
            if (current.hash == h && keyEquals(current.key, key)) {
                V oldValue = current.value;
                current.value = value;
                return oldValue; // return previous value
            }
            current = current.next;
        }

        // Key not found — insert at head of chain
        buckets[index] = new Entry<>(key, value, h, buckets[index]);
        size++;

        // Resize if load factor exceeded
        if (size > buckets.length * loadFactor) {
            resize();
        }
        return null;
    }

    // ─── GET ───
    public V get(K key) {
        int h = hash(key);
        int index = bucketIndex(h);

        Entry<K, V> current = buckets[index];
        while (current != null) {
            if (current.hash == h && keyEquals(current.key, key)) {
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    // ─── REMOVE ───
    public V remove(K key) {
        int h = hash(key);
        int index = bucketIndex(h);

        Entry<K, V> current = buckets[index];
        Entry<K, V> prev = null;

        while (current != null) {
            if (current.hash == h && keyEquals(current.key, key)) {
                if (prev == null) {
                    buckets[index] = current.next; // remove head
                } else {
                    prev.next = current.next; // unlink node
                }
                size--;
                return current.value;
            }
            prev = current;
            current = current.next;
        }
        return null;
    }

    // ─── CONTAINS KEY ───
    public boolean containsKey(K key) {
        int h = hash(key);
        int index = bucketIndex(h);

        Entry<K, V> current = buckets[index];
        while (current != null) {
            if (current.hash == h && keyEquals(current.key, key)) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    // ─── SIZE & EMPTY ───
    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ─── RESIZE (rehash) ───
    @SuppressWarnings("unchecked")
    private void resize() {
        int newCapacity = buckets.length * 2;
        Entry<K, V>[] newBuckets = new Entry[newCapacity];

        // Rehash every entry into the new array
        for (Entry<K, V> head : buckets) {
            Entry<K, V> current = head;
            while (current != null) {
                Entry<K, V> next = current.next; // save next before we overwrite it
                int newIndex = current.hash & (newCapacity - 1);
                current.next = newBuckets[newIndex]; // insert at head of new chain
                newBuckets[newIndex] = current;
                current = next;
            }
        }
        buckets = newBuckets;
    }

    // ─── Key equality (handles null) ───
    private boolean keyEquals(K a, K b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    // ─── Debug: print bucket distribution ───
    public void printBucketDistribution() {
        System.out.println("Capacity: " + buckets.length + ", Size: " + size
                + ", Load: " + String.format("%.2f", (float) size / buckets.length));
        for (int i = 0; i < buckets.length; i++) {
            int count = 0;
            Entry<K, V> e = buckets[i];
            while (e != null) { count++; e = e.next; }
            if (count > 0) {
                System.out.println("  Bucket[" + i + "] → " + count + " entries");
            }
        }
    }
}

