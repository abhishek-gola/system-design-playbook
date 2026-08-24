/**
 * The whole pattern is this one line.
 *
 * Keep it narrow. One decision in, one boolean out. The moment this interface
 * grows a second parameter you are copying the limiter into every
 * implementation, and each new algorithm has to reimplement all of it.
 */
public interface RateLimitStrategy {
    boolean allow(String key);

    /** For the demo output only — real strategies don't need to name themselves. */
    String describe();
}
