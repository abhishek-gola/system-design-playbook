import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One counter per key per window. Cheapest possible, and flawed in a way you
 * should name before the interviewer does.
 *
 * The boundary problem: with a limit of 5 per minute, a client can send 5 at
 * 11:59:59 and 5 more at 12:00:00 — ten requests in one second, both windows
 * technically respected. The demo shows this happening.
 *
 * Ship it anyway when memory is the binding constraint and 2x the limit for one
 * instant is survivable. Otherwise use a sliding window counter, which
 * interpolates the previous window's count and gets most of the accuracy for
 * almost none of the memory.
 */
public class FixedWindowCounterStrategy implements RateLimitStrategy {

    private static final class Window {
        long startMillis;
        int count;
    }

    private final int limit;
    private final long windowMillis;
    private final Ticker ticker;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowCounterStrategy(int limit, long windowMillis, Ticker ticker) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.ticker = ticker;
    }

    @Override
    public boolean allow(String key) {
        Window window = windows.computeIfAbsent(key, k -> new Window());

        synchronized (window) {
            long now = ticker.millis();
            long currentWindowStart = now - (now % windowMillis);

            if (window.startMillis != currentWindowStart) {
                window.startMillis = currentWindowStart;
                window.count = 0;
            }

            if (window.count < limit) {
                window.count++;
                return true;
            }
            return false;
        }
    }

    @Override
    public String describe() {
        return "fixed window counter, " + limit + " per " + (windowMillis / 1000) + "s";
    }
}
