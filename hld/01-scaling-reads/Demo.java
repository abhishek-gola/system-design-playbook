import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Four experiments on the same cache-aside cache, printing the only number that
 * matters in this pattern: how many times the database was actually asked.
 *
 * The point of running this rather than reading about it is the gap between (b)
 * and (c). Everyone believes the stampede exists in the abstract. Seeing "16
 * database loads" turn into "1 database load" from one double-checked lock is
 * what makes the fix stick, and it is a fix you can describe in two sentences
 * at a whiteboard.
 */
public class Demo {

    private static final int THREADS = 16;
    private static final long TTL = 1_000L;
    private static final long DB_LATENCY = 50L;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Scaling reads: cache-aside under pressure ===");
        warmKeySingleThreaded();
        stampede();
        singleFlight();
        jitteredTtls();
        closing();
    }

    /**
     * The happy case, and the reason anyone caches at all. Five reads of the
     * same key cost one query, and the sixth costs another only because the
     * clock has moved past the TTL.
     */
    private static void warmKeySingleThreaded() {
        ManualTicker ticker = new ManualTicker(0);
        FakeDatabase db = new FakeDatabase(DB_LATENCY);
        Cache cache = new NaiveCache(db, ticker, TTL, 0);

        for (int i = 0; i < 5; i++) {
            cache.get("hot");
        }
        int afterWarm = db.loadCount();

        ticker.advanceTo(TTL + 1);          // the entry is now stale
        cache.get("hot");

        System.out.println();
        System.out.println("(a) one key, one thread, six reads");
        System.out.printf("    first five reads          %d database load%n", afterWarm);
        System.out.printf("    sixth read, past the TTL  %d database load%n", db.loadCount() - afterWarm);
        System.out.printf("    total                     %d loads for 6 reads%n", db.loadCount());
    }

    /**
     * The same cache, the same key, and the only change is that the readers
     * arrive together at the instant the entry expires. This is what a viral
     * link looks like from the database's point of view.
     */
    private static void stampede() throws InterruptedException {
        ManualTicker ticker = new ManualTicker(0);
        FakeDatabase db = new FakeDatabase(DB_LATENCY);
        Cache cache = new NaiveCache(db, ticker, TTL, 0);

        cache.get("hot");                   // warm it, one load
        db.resetCount();
        ticker.advanceTo(TTL + 1);          // and now expire it under everyone at once

        hammer(cache, "hot", THREADS);

        System.out.println();
        System.out.println("(b) " + cache.name());
        System.out.printf("    %d threads, one expired key%n", THREADS);
        System.out.printf("    database loads            %d%n", db.loadCount());
        System.out.println("    every thread missed, and every thread queried. On a real hot key");
        System.out.println("    that is your full uncached load arriving in one burst.");
    }

    /**
     * Identical setup, one rule added. Note that the waiters are not served
     * stale data and are not rejected — they block for the length of one query
     * and then take the fresh value, which is why this is an easy sell.
     */
    private static void singleFlight() throws InterruptedException {
        ManualTicker ticker = new ManualTicker(0);
        FakeDatabase db = new FakeDatabase(DB_LATENCY);
        SingleFlightCache cache = new SingleFlightCache(db, ticker, TTL);

        cache.get("hot");
        db.resetCount();
        ticker.advanceTo(TTL + 1);

        hammer(cache, "hot", THREADS);

        System.out.println();
        System.out.println("(c) " + cache.name());
        System.out.printf("    %d threads, one expired key%n", THREADS);
        System.out.printf("    database loads            %d%n", db.loadCount());
        System.out.printf("    threads that waited       %d%n", cache.waitedCount());
    }

    /**
     * Single flight fixes many callers on one key. This is the other stampede:
     * many keys, written together, expiring together. Nothing about locking
     * helps here — the fix is to stop the expiry times lining up in the first
     * place, which costs one call to a random number generator.
     */
    private static void jitteredTtls() {
        ManualTicker ticker = new ManualTicker(0);
        FakeDatabase db = new FakeDatabase(0);           // no latency needed, this part is single threaded
        NaiveCache fixedTtl = new NaiveCache(db, ticker, TTL, 0);
        NaiveCache jitteredTtl = new NaiveCache(db, ticker, TTL, TTL);

        int keys = 200;
        for (int i = 0; i < keys; i++) {                 // a cache warmed in one pass, as after a deploy
            fixedTtl.get("key-" + i);
            jitteredTtl.get("key-" + i);
        }

        System.out.println();
        System.out.println("(d) " + keys + " keys warmed in the same instant, expiring later");
        System.out.println("    clock       fixed TTL     TTL + jitter");
        long[] samples = {1000, 1200, 1400, 1600, 1800, 2000};
        for (long at : samples) {
            System.out.printf("    t+%-6d  %3d / %d     %3d / %d%n",
                    at,
                    fixedTtl.expiredCountAt(at), keys,
                    jitteredTtl.expiredCountAt(at), keys);
        }
        System.out.println("    the fixed column is a cliff, the jittered column is a ramp.");
        System.out.println("    the ramp is the whole point: the database sees a slope, not a wall.");
    }

    private static void closing() {
        System.out.println();
        System.out.println("The sentence this is all for: \"a hot key that expires is a stampede,");
        System.out.println("so I would single-flight the refill and jitter the TTLs — one query");
        System.out.println("instead of one per caller, and the keys stop expiring in lockstep.\"");
    }

    /**
     * Start N threads, park them all on one latch, then release them together.
     * Without the latch the threads trickle in over a few milliseconds and the
     * first one has already filled the cache, so the stampede quietly does not
     * happen and the demo proves nothing.
     */
    private static void hammer(Cache cache, String key, int threads) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                cache.get(key);
            });
            worker.start();
            workers.add(worker);
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();            // join everything, then print aggregates — never interleave prints
        }
    }
}
