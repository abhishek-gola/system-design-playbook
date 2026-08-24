/**
 * Per subscriber, not global. That is the point.
 *
 * Choosing BLOCK for an audit-log subscriber and DROP_NEWEST for a metrics
 * subscriber in the same system is the answer that reads as production
 * experience.
 */
public enum OverflowPolicy {
    /** The next event supersedes this one. Metrics, presence, gauges. */
    DROP_NEWEST,

    /** Slow the whole system rather than lose an event. Ordering-critical work. */
    BLOCK,

    /** Keep it, deal with it later — but only if you can say who drains the DLQ. */
    DEAD_LETTER
}
