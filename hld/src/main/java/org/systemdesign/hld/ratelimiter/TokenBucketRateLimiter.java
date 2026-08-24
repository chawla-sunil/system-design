package org.systemdesign.hld.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Algorithm 1 — Token Bucket.
 *
 * <p>A bucket holds up to {@code capacity} tokens and refills continuously at
 * {@code refillRatePerSec} tokens/second. Every request consumes one token; if the
 * bucket is empty the request is rejected. Allows short bursts up to the capacity
 * while enforcing an average rate equal to the refill rate.
 *
 * <p>Thread-safe: state per key is guarded by synchronizing on the bucket instance.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final double capacity;
    private final double refillRatePerSec;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(double capacity, double refillRatePerSec) {
        if (capacity <= 0 || refillRatePerSec <= 0) {
            throw new IllegalArgumentException("capacity and refillRatePerSec must be > 0");
        }
        this.capacity = capacity;
        this.refillRatePerSec = refillRatePerSec;
    }

    @Override
    public boolean allowRequest(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, nowNanos()));
        synchronized (bucket) {
            refill(bucket);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    private void refill(Bucket bucket) {
        long now = nowNanos();
        double elapsedSec = (now - bucket.lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSec > 0) {
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSec * refillRatePerSec);
            bucket.lastRefillNanos = now;
        }
    }

    private static long nowNanos() {
        return System.nanoTime();
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
