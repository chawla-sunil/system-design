package org.systemdesign.extra.hashmap;

/**
 * Demo / Driver class to test MyHashMap implementation.
 */
public class MyHashMapDemo {

    public static void main(String[] args) {
        MyHashMap<String, Integer> map = new MyHashMap<>();

        // ── Basic put & get ──
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("cherry", 3);
        System.out.println("apple  → " + map.get("apple"));   // 1
        System.out.println("banana → " + map.get("banana"));  // 2
        System.out.println("cherry → " + map.get("cherry"));  // 3
        System.out.println("size   = " + map.size());          // 3

        // ── Update existing key ──
        Integer old = map.put("banana", 20);
        System.out.println("\nUpdated banana (old=" + old + "), new → " + map.get("banana")); // 20

        // ── Remove ──
        map.remove("apple");
        System.out.println("\nAfter removing apple:");
        System.out.println("apple  → " + map.get("apple"));       // null
        System.out.println("containsKey(apple) = " + map.containsKey("apple")); // false
        System.out.println("size   = " + map.size());             // 2

        // ── Null key support ──
        map.put(null, 999);
        System.out.println("\nnull key → " + map.get(null));      // 999

        // ── Trigger resize by adding many entries ──
        MyHashMap<Integer, String> bigMap = new MyHashMap<>(4);
        for (int i = 0; i < 20; i++) {
            bigMap.put(i, "val-" + i);
        }
        System.out.println("\nBig map size = " + bigMap.size());  // 20
        System.out.println("get(15)      = " + bigMap.get(15));   // val-15
        bigMap.printBucketDistribution();

        // ── Collision test: keys with same hashCode ──
        System.out.println("\n── Collision test ──");
        MyHashMap<CollidingKey, String> collisionMap = new MyHashMap<>();
        collisionMap.put(new CollidingKey("a"), "value-a");
        collisionMap.put(new CollidingKey("b"), "value-b");
        collisionMap.put(new CollidingKey("c"), "value-c");
        System.out.println("a → " + collisionMap.get(new CollidingKey("a"))); // value-a
        System.out.println("b → " + collisionMap.get(new CollidingKey("b"))); // value-b
        collisionMap.printBucketDistribution(); // all 3 in same bucket
    }

    /**
     * A key class where ALL instances have the same hashCode → forces collisions.
     * Demonstrates that the map still works correctly via equals().
     */
    static class CollidingKey {
        private final String id;

        CollidingKey(String id) { this.id = id; }

        @Override
        public int hashCode() { return 42; } // constant hash → all collide

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CollidingKey)) return false;
            return id.equals(((CollidingKey) o).id);
        }
    }
}

