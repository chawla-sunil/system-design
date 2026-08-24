package org.systemdesign.hld.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Algorithm 2 — Leaking Bucket (as a meter).
 *
 * <p>Models a FIFO queue of fixed {@code capacity} that "leaks" (drains) at a fixed
 * {@code leakRatePerSec}. Each accepted request adds one unit of water to the bucket;
 * if adding would overflow the capacity the request is dropped. Output is smoothed to a
 * steady rate regardless of bursty input.
 *
 * <p>We track the queue as a fractional level rather than materializing every request,
 * which is the standard memory-efficient counter form of the algorithm.
 *
 * <p>Thread-safe: state per key is guarded by synchronizing on the bucket instance.
 */
public class LeakingBucketRateLimiter implements RateLimiter {

    private final double capacity;
    private final double leakRatePerSec;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LeakingBucketRateLimiter(double capacity, double leakRatePerSec) {
        if (capacity <= 0 || leakRatePerSec <= 0) {
            throw new IllegalArgumentException("capacity and leakRatePerSec must be > 0");
        }
        this.capacity = capacity;
        this.leakRatePerSec = leakRatePerSec;
    }

    @Override
    public boolean allowRequest(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(0.0, nowNanos()));
        synchronized (bucket) {
            leak(bucket);
            if (bucket.level + 1.0 <= capacity) {
                bucket.level += 1.0;
                return true;
            }
            return false;
        }
    }

    private void leak(Bucket bucket) {
        long now = nowNanos();
        double elapsedSec = (now - bucket.lastLeakNanos) / 1_000_000_000.0;
        if (elapsedSec > 0) {
            bucket.level = Math.max(0.0, bucket.level - elapsedSec * leakRatePerSec);
            bucket.lastLeakNanos = now;
        }
    }

    private static long nowNanos() {
        return System.nanoTime();
    }

    private static final class Bucket {
        double level;
        long lastLeakNanos;

        Bucket(double level, long lastLeakNanos) {
            this.level = level;
            this.lastLeakNanos = lastLeakNanos;
        }
    }
}
