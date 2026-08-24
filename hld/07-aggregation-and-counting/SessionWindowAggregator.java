import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Windows bounded by inactivity rather than by the clock. "A browsing session
 * ends when the user has been quiet for ninety seconds."
 *
 * This is the one that behaves differently from the other two, and the
 * difference is worth being precise about in an interview: a session window
 * cannot be assigned from a timestamp alone. Every event opens a session of its
 * own, and then absorbs any existing session it now touches. An event landing
 * in the gap between two sessions merges them into one.
 *
 * That merging step is the part candidates forget, and it is the reason session
 * windows are more expensive to checkpoint: the set of open windows changes
 * shape as events arrive rather than only growing.
 */
public final class SessionWindowAggregator {

    /** Mutable on purpose: sessions grow and merge in place as the stream advances. */
    public static final class Session {
        public final String key;
        public long start;
        public long end;
        public long count;

        Session(String key, long timestampMs) {
            this.key = key;
            this.start = timestampMs;
            this.end = timestampMs;
            this.count = 0;
        }

        public long durationMs() {
            return end - start;
        }
    }

    private final long gapMs;
    private final Map<String, List<Session>> open = new TreeMap<>();

    public SessionWindowAggregator(long gapMs) {
        this.gapMs = gapMs;
    }

    public void add(ClickEvent e) {
        long ts = e.eventTimeMs();
        List<Session> existing = open.computeIfAbsent(e.userId(), k -> new ArrayList<>());

        Session merged = new Session(e.userId(), ts);
        merged.count = 1;

        List<Session> keep = new ArrayList<>();
        for (Session s : existing) {
            boolean touches = ts >= s.start - gapMs && ts <= s.end + gapMs;
            if (touches) {
                merged.start = Math.min(merged.start, s.start);
                merged.end = Math.max(merged.end, s.end);
                merged.count += s.count;
            } else {
                keep.add(s);
            }
        }
        keep.add(merged);
        open.put(e.userId(), keep);
    }

    public long gapMs() {
        return gapMs;
    }

    public Map<String, List<Session>> sessions() {
        for (List<Session> list : open.values()) {
            list.sort((a, b) -> Long.compare(a.start, b.start));
        }
        return open;
    }

    public int totalSessions() {
        int total = 0;
        for (List<Session> list : open.values()) {
            total += list.size();
        }
        return total;
    }
}
