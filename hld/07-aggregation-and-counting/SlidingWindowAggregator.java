import java.util.Map;
import java.util.TreeMap;

/**
 * Overlapping windows. "Clicks in the last three minutes, refreshed every
 * minute." The sheet's example is five minutes refreshed every thirty seconds,
 * which is the same code with different constants; the numbers here are smaller
 * only so the printed output fits on a screen.
 *
 * The cost that catches people out: every event is written into size/slide
 * windows, so a five-minute window sliding every thirty seconds multiplies your
 * state and your write amplification by ten. When an interviewer asks why your
 * state backend is under pressure, this ratio is usually the answer.
 *
 * The way out, if the aggregation is invertible (counts and sums are, maxima
 * are not), is to keep per-slide buckets and add up the last N of them on read.
 * Say that out loud — it is the difference between having used sliding windows
 * and having read about them.
 */
public final class SlidingWindowAggregator {

    private final long sizeMs;
    private final long slideMs;
    private final TreeMap<Long, Long> counts = new TreeMap<>();

    public SlidingWindowAggregator(long sizeMs, long slideMs) {
        this.sizeMs = sizeMs;
        this.slideMs = slideMs;
    }

    public void add(ClickEvent e) {
        long ts = e.eventTimeMs();
        // Every window whose start is a multiple of the slide and whose span
        // covers this timestamp. Walking backwards from the nearest slide
        // boundary is the whole assigner.
        long firstStart = Math.floorDiv(ts, slideMs) * slideMs;
        for (long start = firstStart; start > ts - sizeMs; start -= slideMs) {
            if (start < 0) {
                break;
            }
            counts.merge(start, 1L, Long::sum);
        }
    }

    public long sizeMs() {
        return sizeMs;
    }

    public long slideMs() {
        return slideMs;
    }

    public int windowsPerEvent() {
        return (int) (sizeMs / slideMs);
    }

    public Map<Long, Long> results() {
        return counts;
    }
}
