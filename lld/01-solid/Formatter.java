/**
 * S — one reason to change: what the output looks like.
 * O — a new format is a new class, and Logger never learns about it.
 */
public interface Formatter {
    String format(LogMessage message);
}
