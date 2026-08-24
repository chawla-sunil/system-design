package org.systemdesign.hld.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Algorithm 3 — Fixed Window Counter.
 *
 * <p>Time is divided into fixed windows of {@code windowMillis}. Each window keeps a
 * counter; a request is allowed while the counter is below {@code limit}, otherwise it is
 * rejected. When the wall-clock crosses into a new window the counter resets.
 *
 * <p>Simple and memory efficient, but suffers from the boundary burst problem: up to
 * {@code 2 * limit} requests can pass in a short span straddling a window edge.
 *
 * <p>Thread-safe: state per key is guarded by synchronizing on the window instance.
 */
public class FixedWindowCounterRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowCounterRateLimiter(long limit, long windowMillis) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and windowMillis must be > 0");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean allowRequest(String key) {
        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            long currentWindowStart = (now() / windowMillis) * windowMillis;
            if (currentWindowStart != window.windowStart) {
                window.windowStart = currentWindowStart;
                window.count = 0;
            }
            if (window.count < limit) {
                window.count++;
                return true;
            }
            return false;
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static final class Window {
        long windowStart = -1;
        long count;
    }
}
