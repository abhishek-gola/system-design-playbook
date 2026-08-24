import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keep a timestamp per accepted request, drop the ones that have fallen out of
 * the window, and compare what's left against the limit.
 *
 * Exact, with no boundary flaw and no burst. The cost is memory: one timestamp
 * per request per key, so a limit of 10,000 a minute means 10,000 longs sitting
 * in memory for every active client.
 *
 * Pick this when the limit is a contractual promise — a payment provider that
 * allows exactly 100 calls a minute and starts charging you at 101.
 */
public class SlidingWindowLogStrategy implements RateLimitStrategy {

    private final int limit;
    private final long windowMillis;
    private final Ticker ticker;
    private final Map<String, Deque<Long>> log = new ConcurrentHashMap<>();

    public SlidingWindowLogStrategy(int limit, long windowMillis, Ticker ticker) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.ticker = ticker;
    }

    @Override
    public boolean allow(String key) {
        Deque<Long> timestamps = log.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            long now = ticker.millis();
            long cutoff = now - windowMillis;

            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }

            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return true;
            }
            return false;
        }
    }

    @Override
    public String describe() {
        return "sliding window log, " + limit + " per " + (windowMillis / 1000) + "s";
    }
}
