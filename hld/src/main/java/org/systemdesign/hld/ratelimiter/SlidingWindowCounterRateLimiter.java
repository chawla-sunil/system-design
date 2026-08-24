package org.systemdesign.hld.ratelimiter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Algorithm 5 — Sliding Window Counter (recommended).
 *
 * <p>Hybrid of Fixed Window Counter and Sliding Window Log. Keeps a counter for the current
 * fixed window and the previous one, then estimates the request count over the trailing
 * {@code windowMillis} using a weighted average:
 *
 * <pre>
 *   weighted = prevCount * (1 - elapsedFractionOfCurrentWindow) + currCount
 * </pre>
 *
 * <p>A request is allowed while {@code weighted < limit}. This smooths the fixed-window
 * boundary spike using only two counters per key (memory efficient) with ~99.97% accuracy.
 *
 * <p>Thread-safe: state per key is guarded by synchronizing on the window instance.
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final long limit;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Windows> state = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(long limit, long windowMillis) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and windowMillis must be > 0");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean allowRequest(String key) {
        Windows w = state.computeIfAbsent(key, k -> new Windows());
        synchronized (w) {
            long now = now();
            long currentWindowStart = (now / windowMillis) * windowMillis;
            rollWindowsIfNeeded(w, currentWindowStart);

            double elapsedFraction = (now - w.currWindowStart) / (double) windowMillis;
            double weighted = w.prevCount * (1.0 - elapsedFraction) + w.currCount;

            if (weighted < limit) {
                w.currCount++;
                return true;
            }
            return false;
        }
    }

    private void rollWindowsIfNeeded(Windows w, long currentWindowStart) {
        if (w.currWindowStart == currentWindowStart) {
            return;
        }
        if (currentWindowStart - w.currWindowStart == windowMillis) {
            // Advanced exactly one window: current becomes previous.
            w.prevCount = w.currCount;
        } else {
            // Skipped one or more windows: previous window contributed nothing.
            w.prevCount = 0;
        }
        w.currCount = 0;
        w.currWindowStart = currentWindowStart;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static final class Windows {
        long currWindowStart = -1;
        long currCount;
        long prevCount;
    }
}
