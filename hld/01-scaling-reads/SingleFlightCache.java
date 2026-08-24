import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The same cache-aside logic with one rule added: for any given key, exactly one
 * caller is allowed to be loading from the database at a time. Everyone else
 * queues behind it and takes the value it produces.
 *
 * This is the answer to "what happens when a hot key expires", and it is worth
 * being precise about what it does and does not fix. It collapses N concurrent
 * misses on ONE key into one query. It does nothing about N different keys
 * expiring together — that is what jittered TTLs are for — and nothing about a
 * cold cache after a restart, which is what warming is for.
 *
 * The lock is per key, taken from a map, so callers for different keys never
 * wait on each other. A single global lock would fix the stampede and replace
 * it with a much worse problem.
 */
public class SingleFlightCache implements Cache {

    private record Entry(String value, long expiresAt) { }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    // One lock object per key. These are never removed, which is fine for a
    // demo and is the first thing to fix in production: a long-lived process
    // with unbounded key cardinality leaks an object per key seen. The usual
    // fixes are a bounded map, or a map of futures where the entry is removed
    // as soon as the load completes. Expect to be asked about this.
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();
    private final AtomicInteger waited = new AtomicInteger();

    private final FakeDatabase db;
    private final Ticker ticker;
    private final long ttlMillis;

    public SingleFlightCache(FakeDatabase db, Ticker ticker, long ttlMillis) {
        this.db = db;
        this.ticker = ticker;
        this.ttlMillis = ttlMillis;
    }

    @Override
    public String get(String key) {
        Entry live = liveEntry(key);
        if (live != null) {
            return live.value();          // fast path: no lock on a hit
        }

        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            // Second check, now holding the lock. Everyone who queued while the
            // winner was loading lands here and finds the value already there.
            // Skipping this check is the classic bug: the queue drains one at a
            // time and each of them repeats the query, so you have serialised
            // the stampede rather than removed it.
            Entry refreshed = liveEntry(key);
            if (refreshed != null) {
                waited.incrementAndGet();
                return refreshed.value();
            }
            String value = db.load(key);
            entries.put(key, new Entry(value, ticker.millis() + ttlMillis));
            return value;
        }
    }

    private Entry liveEntry(String key) {
        Entry entry = entries.get(key);
        if (entry != null && entry.expiresAt() > ticker.millis()) {
            return entry;
        }
        return null;
    }

    /** Callers that queued behind the one loader instead of hitting the database. */
    public int waitedCount() {
        return waited.get();
    }

    @Override
    public String name() {
        return "single-flight cache-aside, TTL " + ttlMillis + "ms";
    }
}
