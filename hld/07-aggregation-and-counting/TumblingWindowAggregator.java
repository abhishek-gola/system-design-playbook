import java.util.Map;
import java.util.TreeMap;

/**
 * Fixed, non-overlapping windows. "Clicks per minute."
 *
 * The whole assigner is one line of arithmetic: an event's window is decided by
 * its timestamp alone, with no reference to any other event. That is why
 * tumbling windows are cheap to parallelise and cheap to recover — an operator
 * rebuilding state after a failure does not need to know what order the events
 * came back in.
 *
 * The number an interviewer will push on: state size. One counter per window
 * per key, and windows are dropped once they fire, so memory is bounded by how
 * many windows you keep open, not by how much traffic flows through.
 */
public final class TumblingWindowAggregator {

    private final long sizeMs;
    private final TreeMap<Long, Long> counts = new TreeMap<>();

    public TumblingWindowAggregator(long sizeMs) {
        this.sizeMs = sizeMs;
    }

    public void add(ClickEvent e) {
        counts.merge(windowStart(e.eventTimeMs(), sizeMs), 1L, Long::sum);
    }

    /** floorDiv rather than a plain divide, because event times can be negative once you have real epochs and time zones. */
    public static long windowStart(long timestampMs, long sizeMs) {
        return Math.floorDiv(timestampMs, sizeMs) * sizeMs;
    }

    public long sizeMs() {
        return sizeMs;
    }

    public Map<Long, Long> results() {
        return counts;
    }
}
