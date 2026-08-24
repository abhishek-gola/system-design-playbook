import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache-aside written the way everyone writes it the first time: look, miss,
 * load, put. Correct, and it is genuinely the right starting point — the bug is
 * not in the logic, it is in what happens when a thousand callers run this same
 * correct logic at the same instant on the same key.
 *
 * The gap between the read and the put is unguarded, so every caller that
 * arrives inside that window loads independently. Under normal traffic that
 * costs you a handful of duplicated queries and nobody notices. On a hot key it
 * is a stampede, and the database sees its full uncached load in one burst at
 * the exact moment it is least able to absorb it.
 *
 * The class also carries the TTL jitter, because jitter is a property of the
 * expiry policy rather than of the locking. Two keys written in the same second
 * with the same TTL expire in the same millisecond, and a million keys doing
 * that is a stampede that no amount of single-flight can help with — single
 * flight collapses many callers on one key, not many keys at once.
 */
public class NaiveCache implements Cache {

    private record Entry(String value, long expiresAt) { }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final FakeDatabase db;
    private final Ticker ticker;
    private final long ttlMillis;
    private final long jitterMillis;

    // Fixed seed so the jitter demo prints the same table on every machine.
    private final Random random = new Random(42);

    public NaiveCache(FakeDatabase db, Ticker ticker, long ttlMillis, long jitterMillis) {
        this.db = db;
        this.ticker = ticker;
        this.ttlMillis = ttlMillis;
        this.jitterMillis = jitterMillis;
    }

    @Override
    public String get(String key) {
        long now = ticker.millis();
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAt() > now) {
            return entry.value();
        }
        // Nothing stops sixteen threads from all arriving here at once.
        String value = db.load(key);
        entries.put(key, new Entry(value, now + ttlWithJitter()));
        return value;
    }

    /**
     * Synchronised because Random is not thread safe and a shared one handed to
     * concurrent callers would produce different sequences per run. The jitter
     * demo is single threaded anyway; this is here so the class stays honest if
     * you reuse it.
     */
    private synchronized long ttlWithJitter() {
        if (jitterMillis <= 0) {
            return ttlMillis;
        }
        return ttlMillis + random.nextInt((int) jitterMillis + 1);
    }

    /** How many cached entries would be expired if the clock read this instant. */
    public int expiredCountAt(long instant) {
        int expired = 0;
        for (Entry entry : entries.values()) {
            if (entry.expiresAt() <= instant) {
                expired++;
            }
        }
        return expired;
    }

    @Override
    public String name() {
        return jitterMillis > 0
                ? "naive cache-aside, TTL " + ttlMillis + "ms + up to " + jitterMillis + "ms jitter"
                : "naive cache-aside, TTL " + ttlMillis + "ms";
    }
}
