package org.systemdesign.hld.ratelimiter;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Algorithm 4 — Sliding Window Log.
 *
 * <p>Keeps a log of the timestamps of every request within the last {@code windowMillis}.
 * On each request, timestamps older than the window are evicted, then the remaining count
 * is compared to {@code limit}: if strictly below the limit the request is allowed and its
 * timestamp is appended, otherwise it is rejected.
 *
 * <p>Exact (no boundary problem) but memory-heavy: it stores one timestamp per request in
 * the window. Mirrors a Redis sorted-set implementation (ZADD / ZREMRANGEBYSCORE / ZCARD).
 *
 * <p>Thread-safe: state per key is guarded by synchronizing on the log deque.
 */
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(long limit, long windowMillis) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and windowMillis must be > 0");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean allowRequest(String key) {
        ArrayDeque<Long> log = logs.computeIfAbsent(key, k -> new ArrayDeque<>());
        long now = now();
        long windowStart = now - windowMillis;
        synchronized (log) {
            while (!log.isEmpty() && log.peekFirst() <= windowStart) {
                log.pollFirst();
            }
            if (log.size() < limit) {
                log.addLast(now);
                return true;
            }
            return false;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
