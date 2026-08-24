import java.util.Map;
import java.util.TreeMap;

/**
 * The same tumbling windows, assigned by when the event reached the operator
 * rather than when it happened.
 *
 * Note that add() deliberately ignores the event's own timestamp. That is not a
 * simplification, it is the definition: processing time asks "what time is it
 * here, now", which means the answer depends on how fast your consumer happened
 * to be running. Replay the identical topic tomorrow after a backlog and you
 * get different numbers out. For an ad click aggregator that bills advertisers,
 * that is disqualifying.
 *
 * Processing time is still the right choice sometimes — monitoring a consumer's
 * own throughput, or any window whose meaning genuinely is "in the last minute
 * of real time". Knowing when it is acceptable is the follow-up question.
 *
 * The clock here is a synthetic counter, not System.currentTimeMillis(), so the
 * demo is reproducible.
 */
public final class ProcessingTimeWindower {

    private final long sizeMs;
    private final long tickMs;
    private final TreeMap<Long, Long> counts = new TreeMap<>();
    private long clock = 0;

    public ProcessingTimeWindower(long sizeMs, long tickMs) {
        this.sizeMs = sizeMs;
        this.tickMs = tickMs;
    }

    public void add(ClickEvent event) {
        counts.merge(Math.floorDiv(clock, sizeMs) * sizeMs, 1L, Long::sum);
        clock += tickMs;
    }

    public Map<Long, Long> results() {
        return counts;
    }
}
