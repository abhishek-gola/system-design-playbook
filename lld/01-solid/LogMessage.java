import java.time.Instant;
import java.util.Map;

/**
 * S — one reason to change: the shape of a log record.
 *
 * Immutable, because a log record that can be edited after the fact is a log
 * record you cannot trust.
 */
public final class LogMessage {
    private final Instant at;
    private final LogLevel level;
    private final String logger;
    private final String text;
    private final Map<String, String> context;

    public LogMessage(Instant at, LogLevel level, String logger,
                      String text, Map<String, String> context) {
        this.at = at;
        this.level = level;
        this.logger = logger;
        this.text = text;
        this.context = Map.copyOf(context);
    }

    public Instant at()               { return at; }
    public LogLevel level()           { return level; }
    public String logger()            { return logger; }
    public String text()              { return text; }
    public Map<String, String> context() { return context; }
}
