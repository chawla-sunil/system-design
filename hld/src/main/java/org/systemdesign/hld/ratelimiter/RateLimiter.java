package org.systemdesign.hld.ratelimiter;

/**
 * Common contract for all rate limiting algorithms.
 *
 * <p>Implementations are keyed (e.g. per user, per IP, per API key) and must be
 * safe to call concurrently from multiple threads for the same or different keys.
 */
public interface RateLimiter {

    /**
     * Decide whether a single request identified by {@code key} is allowed right now.
     *
     * @param key identity to rate limit on (e.g. "user:123")
     * @return {@code true} if the request is allowed, {@code false} if it should be rejected
     */
    boolean allowRequest(String key);
}
