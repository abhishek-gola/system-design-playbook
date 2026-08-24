import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Tumbling windows on event time, with a watermark, allowed lateness and a side
 * output. This is the class the whole folder is really about.
 *
 * A watermark is not a filter and it is not a clock. It is the operator's
 * assertion: "I do not expect to see any more events with a timestamp below
 * this." Everything downstream keys off that assertion — a window fires when
 * the watermark passes its end, because that is the moment the operator
 * believes the window is complete.
 *
 * The generator used here is the common one: watermark = highest event time
 * seen so far, minus a fixed tolerance for out-of-orderness. That tolerance is
 * a straight latency-for-completeness trade. Thirty seconds means every result
 * is thirty seconds stale and you catch nearly all stragglers. One second means
 * fresh results and more corrections.
 *
 * Three things happen to an event depending on where it lands:
 *
 *   before the watermark, window still open   -> counted normally
 *   after the window fired, inside lateness   -> counted, and the window re-fires
 *                                                with a corrected total
 *   after window end + allowed lateness       -> side output, because the state
 *                                                is gone and there is nothing
 *                                                left to correct
 *
 * That third case is the one worth being loud about. Flink's default is to drop
 * those events silently. Routing them to a side output instead means you can
 * count them, alert on them, and feed them to the batch path that reprocesses
 * from object storage — which is what a lambda architecture actually is.
 */
public final class EventTimeWindower {

    public record Firing(long windowStart, long windowEnd, long count, boolean lateCorrection) {
    }

    /**
     * A side-output event, carrying the watermark as it stood when the event
     * turned up. That second number is the useful one operationally: it tells
     * you how far past the tolerance the event actually was, which is what you
     * need before deciding whether to raise the tolerance or accept the loss.
     */
    public record LateArrival(ClickEvent event, long watermarkAtArrivalMs) {
    }

    private final long sizeMs;
    private final long maxOutOfOrdernessMs;
    private final long allowedLatenessMs;

    private final TreeMap<Long, Long> openWindows = new TreeMap<>();
    private final Set<Long> alreadyFired = new HashSet<>();
    private final List<Firing> firings = new ArrayList<>();
    private final List<LateArrival> sideOutput = new ArrayList<>();

    private long maxEventTimeMs = -1;

    // Synthetic event times all start at zero, so -1 is safely "before
    // everything". Flink uses Long.MIN_VALUE; the only reason not to here is
    // that subtracting the tolerance from it would overflow.
    private long watermarkMs = -1;

    public EventTimeWindower(long sizeMs, long maxOutOfOrdernessMs, long allowedLatenessMs) {
        this.sizeMs = sizeMs;
        this.maxOutOfOrdernessMs = maxOutOfOrdernessMs;
        this.allowedLatenessMs = allowedLatenessMs;
    }

    public void add(ClickEvent e) {
        long ts = e.eventTimeMs();
        long start = Math.floorDiv(ts, sizeMs) * sizeMs;
        long end = start + sizeMs;

        if (end + allowedLatenessMs <= watermarkMs) {
            // The window's state was already dropped. There is no counter left
            // to increment, which is precisely why this branch exists.
            sideOutput.add(new LateArrival(e, watermarkMs));
            return;
        }

        long updated = openWindows.merge(start, 1L, Long::sum);
        if (alreadyFired.contains(start)) {
            firings.add(new Firing(start, end, updated, true));
        }

        maxEventTimeMs = Math.max(maxEventTimeMs, ts);
        advanceWatermark(maxEventTimeMs - maxOutOfOrdernessMs);
    }

    /** End of a bounded stream: push the watermark past everything so nothing is left open. */
    public void endOfStream() {
        advanceWatermark(maxEventTimeMs + sizeMs + allowedLatenessMs);
    }

    private void advanceWatermark(long candidate) {
        // Watermarks are monotonic by definition. A generator that let one go
        // backwards would be un-asserting something it already promised, and
        // every downstream operator would be entitled to misbehave.
        if (candidate <= watermarkMs) {
            return;
        }
        watermarkMs = candidate;

        for (Map.Entry<Long, Long> entry : new ArrayList<>(openWindows.entrySet())) {
            long start = entry.getKey();
            long end = start + sizeMs;
            if (end <= watermarkMs && !alreadyFired.contains(start)) {
                alreadyFired.add(start);
                firings.add(new Firing(start, end, entry.getValue(), false));
            }
            if (end + allowedLatenessMs <= watermarkMs) {
                // State eviction. Without this the job's memory grows forever,
                // and "why is my Flink job's state unbounded" is nearly always
                // a window that never gets a watermark past its end.
                openWindows.remove(start);
            }
        }
    }

    public List<Firing> firings() {
        return firings;
    }

    public List<LateArrival> sideOutput() {
        return sideOutput;
    }

    public long watermarkMs() {
        return watermarkMs;
    }

    /** Counts as they finally stand, late corrections included. */
    public Map<Long, Long> finalCounts() {
        Map<Long, Long> out = new TreeMap<>();
        for (Firing f : firings) {
            out.put(f.windowStart(), f.count());
        }
        return out;
    }
}
