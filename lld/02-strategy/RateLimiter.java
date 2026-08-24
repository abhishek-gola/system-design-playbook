import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The context. Notice what isn't in it: any knowledge of an algorithm, and any
 * `if` that names one.
 *
 * The route -> strategy map is the answer to "make the limit different per
 * endpoint". Loading that map from config is the answer to "let ops change it
 * without a deploy". Both follow-ups, and neither needs a line of new logic.
 */
public class RateLimiter {
    private final Map<String, RateLimitStrategy> byRoute = new ConcurrentHashMap<>();
    private final RateLimitStrategy fallback;

    public RateLimiter(RateLimitStrategy fallback) {
        this.fallback = fallback;
    }

    /** Called at startup from config, and again whenever ops change it. */
    public RateLimiter register(String route, RateLimitStrategy strategy) {
        byRoute.put(route, strategy);
        return this;
    }

    public boolean isAllowed(Request request) {
        return strategyFor(request.route()).allow(request.clientKey());
    }

    public RateLimitStrategy strategyFor(String route) {
        return byRoute.getOrDefault(route, fallback);
    }
}
