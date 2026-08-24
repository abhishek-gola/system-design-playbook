import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capacity tokens in a bucket, refilled at a steady rate. A request costs one
 * token; no tokens means rejected.
 *
 * The default answer for an API gateway. Two numbers of state per key, and the
 * burst up to `capacity` is usually a feature — clients retry in clusters and
 * you would rather absorb that than reject it.
 *
 * Refill is lazy: rather than a background thread topping up every bucket, each
 * call works out how many tokens should have arrived since it last looked. That
 * is what makes this cheap at a million keys.
 */
public class TokenBucketStrategy implements RateLimitStrategy {

    private static final class Bucket {
        double tokens;
        long lastRefillMillis;
    }

    private final int capacity;
    private final double tokensPerMilli;
    private final Ticker ticker;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketStrategy(int capacity, double tokensPerSecond, Ticker ticker) {
        this.capacity = capacity;
        this.tokensPerMilli = tokensPerSecond / 1000.0;
        this.ticker = ticker;
    }

    @Override
    public boolean allow(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> {
            Bucket b = new Bucket();
            b.tokens = capacity;
            b.lastRefillMillis = ticker.millis();
            return b;
        });

        // Per-key lock, not one global lock. Two clients never wait on each other.
        synchronized (bucket) {
            long now = ticker.millis();
            double refill = (now - bucket.lastRefillMillis) * tokensPerMilli;
            bucket.tokens = Math.min(capacity, bucket.tokens + refill);
            bucket.lastRefillMillis = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    @Override
    public String describe() {
        return "token bucket, capacity " + capacity;
    }
}
