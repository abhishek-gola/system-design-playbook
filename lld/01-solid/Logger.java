import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S — one reason to change: how logging is orchestrated. Which is almost never.
 * D — depends on the Formatter and Sink interfaces, and receives both through
 *     the constructor. It has never heard of a file.
 * O — a new format or a new destination is a new class. Nothing in here moves.
 *
 * Count the `if`s: one, and it's a level threshold, not a type check.
 */
public class Logger {
    private final String name;
    private final LogLevel threshold;
    private final Formatter formatter;
    private final List<Sink> sinks;
    private final Map<String, String> context = new HashMap<>();

    public Logger(String name, LogLevel threshold, Formatter formatter, List<Sink> sinks) {
        this.name = name;
        this.threshold = threshold;
        this.formatter = formatter;
        this.sinks = List.copyOf(sinks);
    }

    /** Fluent context, so callers don't rebuild a map on every call. */
    public Logger with(String key, String value) {
        context.put(key, value);
        return this;
    }

    public void debug(String text) { log(LogLevel.DEBUG, text); }
    public void info(String text)  { log(LogLevel.INFO, text); }
    public void warn(String text)  { log(LogLevel.WARN, text); }
    public void error(String text) { log(LogLevel.ERROR, text); }

    public void log(LogLevel level, String text) {
        if (!level.atLeast(threshold)) return;

        LogMessage message = new LogMessage(Instant.now(), level, name, text, context);
        String line = formatter.format(message);

        for (Sink sink : sinks) {
            // One broken sink must not swallow the others. This is the same
            // per-subscriber isolation that Observer needs — see lld/05-observer.
            try {
                sink.write(line);
            } catch (RuntimeException e) {
                System.err.println("sink " + sink.getClass().getSimpleName()
                        + " failed: " + e.getMessage());
            }
        }
    }

    public void close() {
        for (Sink sink : sinks) {
            try {
                sink.close();
            } catch (RuntimeException e) {
                System.err.println("close failed: " + e.getMessage());
            }
        }
    }
}
