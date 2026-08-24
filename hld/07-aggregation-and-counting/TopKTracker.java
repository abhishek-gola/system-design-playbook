import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Top K from a sketch plus a bounded sorted set.
 *
 * The gap that makes this necessary: a Count-Min Sketch can tell you roughly
 * how often it has seen a key you name, but it cannot list anything. There are
 * no keys inside it, only counters. So it answers "how popular is this video"
 * and cannot answer "what are the popular videos" at all.
 *
 * The fix is to carry a small candidate set alongside it. Every key that comes
 * through gets its estimate looked up, and if that estimate beats the weakest
 * current candidate it takes its place. Memory is K entries plus the sketch,
 * regardless of how many distinct keys the stream contains.
 *
 * In production this candidate set is a Redis sorted set: ZADD the estimate,
 * then ZREMRANGEBYRANK to trim it back to K. The scan for the weakest member
 * below is what the sorted set does for you in log time; it is written out here
 * so the mechanism is visible rather than hidden behind a Redis command.
 *
 * The honest caveat, which is worth offering before you are asked: this is
 * approximate twice over. The counts are sketch estimates, and a key that only
 * becomes hot late in the stream can be evicted before it gets there. For a
 * trending-videos panel that is fine. For anything that decides money, it is
 * not, and the answer there is to use the sketch to shortlist and then count
 * the shortlist exactly.
 */
public final class TopKTracker {

    public record Entry(String key, long estimatedCount) {
    }

    private final CountMinSketch sketch;
    private final int k;
    private final Map<String, Long> candidates = new HashMap<>();
    private long evictions = 0;

    public TopKTracker(CountMinSketch sketch, int k) {
        this.sketch = sketch;
        this.k = k;
    }

    public void offer(String key) {
        sketch.add(key, 1L);
        long estimate = sketch.estimate(key);

        if (candidates.containsKey(key)) {
            candidates.put(key, estimate);
            return;
        }
        if (candidates.size() < k) {
            candidates.put(key, estimate);
            return;
        }

        // Iterate in sorted key order rather than HashMap order, so ties break
        // the same way on every run and the demo output is deterministic.
        List<String> keys = new ArrayList<>(candidates.keySet());
        Collections.sort(keys);
        String weakest = keys.get(0);
        long weakestCount = candidates.get(weakest);
        for (String candidate : keys) {
            long count = candidates.get(candidate);
            if (count < weakestCount) {
                weakest = candidate;
                weakestCount = count;
            }
        }

        if (estimate > weakestCount) {
            candidates.remove(weakest);
            candidates.put(key, estimate);
            evictions++;
        }
    }

    public List<Entry> top() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : candidates.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> {
            int byCount = Long.compare(b.estimatedCount(), a.estimatedCount());
            return byCount != 0 ? byCount : a.key().compareTo(b.key());
        });
        return out;
    }

    public long evictions() {
        return evictions;
    }

    public int trackedKeys() {
        return candidates.size();
    }
}
