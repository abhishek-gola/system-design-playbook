import java.util.concurrent.atomic.AtomicInteger;

/**
 * The thing we are trying to protect.
 *
 * Two jobs: count how many times it was asked for a value, and be slow enough
 * that a stampede is actually observable. The sleep is the one piece of real
 * time in this folder and it exists for a reason — a stampede is a race between
 * "I found nothing in the cache" and "I have put something in the cache", so if
 * the load returned instantly the window would be too narrow to see.
 *
 * Fifty milliseconds is roughly a realistic figure for an uncached query that
 * joins a couple of tables, which is exactly the kind of query people cache.
 */
public class FakeDatabase {

    private final AtomicInteger loads = new AtomicInteger();
    private final long latencyMillis;

    public FakeDatabase(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    public String load(String key) {
        loads.incrementAndGet();
        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "row(" + key + ")";
    }

    public int loadCount() {
        return loads.get();
    }

    public void resetCount() {
        loads.set(0);
    }
}
