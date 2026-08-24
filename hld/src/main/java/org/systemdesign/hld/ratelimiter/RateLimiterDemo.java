package org.systemdesign.hld.ratelimiter;

/**
 * Small runnable demo that exercises each rate limiting algorithm from
 * {@code rate-limiter-hld.md} against a burst of requests for a single key.
 *
 * <p>Run: {@code mvn -q -pl hld exec:java -Dexec.mainClass=org.systemdesign.hld.ratelimiter.RateLimiterDemo}
 * or execute this class' {@code main} directly from the IDE.
 */
public class RateLimiterDemo {

    public static void main(String[] args) throws InterruptedException {
        String key = "user:123";

        System.out.println("=== Algorithm 1: Token Bucket (capacity=4, refill=2/sec) ===");
        demoTokenBucket(key);

        System.out.println("\n=== Algorithm 2: Leaking Bucket (capacity=4, leak=2/sec) ===");
        demoLeakingBucket(key);

        System.out.println("\n=== Algorithm 3: Fixed Window Counter (limit=5, window=1s) ===");
        demoBurst(new FixedWindowCounterRateLimiter(5, 1000), key, 7);

        System.out.println("\n=== Algorithm 4: Sliding Window Log (limit=5, window=1s) ===");
        demoBurst(new SlidingWindowLogRateLimiter(5, 1000), key, 7);

        System.out.println("\n=== Algorithm 5: Sliding Window Counter (limit=5, window=1s) ===");
        demoBurst(new SlidingWindowCounterRateLimiter(5, 1000), key, 7);
    }

    private static void demoTokenBucket(String key) throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(4, 2);
        System.out.println("Burst of 6 requests (bucket starts full with 4 tokens):");
        demoBurst(limiter, key, 6);
        System.out.println("Sleeping 1s to let ~2 tokens refill...");
        Thread.sleep(1000);
        System.out.println("2 more requests after refill:");
        demoBurst(limiter, key, 2);
    }

    private static void demoLeakingBucket(String key) throws InterruptedException {
        LeakingBucketRateLimiter limiter = new LeakingBucketRateLimiter(4, 2);
        System.out.println("Burst of 6 requests (bucket capacity 4):");
        demoBurst(limiter, key, 6);
        System.out.println("Sleeping 1s to let ~2 units leak out...");
        Thread.sleep(1000);
        System.out.println("2 more requests after leak:");
        demoBurst(limiter, key, 2);
    }

    private static void demoBurst(RateLimiter limiter, String key, int requests) {
        int allowed = 0;
        int rejected = 0;
        StringBuilder timeline = new StringBuilder();
        for (int i = 1; i <= requests; i++) {
            boolean ok = limiter.allowRequest(key);
            timeline.append(ok ? "\u2705" : "\u274C");
            if (ok) {
                allowed++;
            } else {
                rejected++;
            }
        }
        System.out.println("  " + timeline + "  (allowed=" + allowed + ", rejected=" + rejected + ")");
    }
}
