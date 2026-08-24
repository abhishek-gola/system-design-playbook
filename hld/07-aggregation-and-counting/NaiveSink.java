import java.util.Map;
import java.util.TreeMap;

/**
 * Writes every record the moment it arrives. At-least-once, and the "at least"
 * is doing real work.
 *
 * There is nothing wrong with this sink in isolation — the job's own state is
 * still checkpointed and still correct. What is wrong is that the sink has
 * already published results derived from records the checkpoint does not cover.
 * When the job restarts from the last complete checkpoint and replays those
 * records, they are counted a second time. The sink has no way to undo the
 * first count because it never knew the writes were provisional.
 *
 * This is fine, and cheaper, when the operation is idempotent: an upsert of a
 * computed value keyed by window, rather than an increment. If an interviewer
 * pushes on exactly-once and you do not want to build two-phase commit, "I made
 * the sink idempotent instead" is a strong answer — as long as you can say why
 * an increment is not idempotent and an upsert is.
 */
public final class NaiveSink implements Sink {

    private final Map<String, Long> store = new TreeMap<>();
    private long writes = 0;

    @Override
    public String name() {
        return "naive sink (increments on every record)";
    }

    @Override
    public void write(String key, long delta) {
        store.merge(key, delta, Long::sum);
        writes++;
    }

    @Override
    public void onBarrier(long checkpointId) {
        // Nothing to prepare: everything is already published.
    }

    @Override
    public void onCheckpointComplete(long checkpointId) {
        // Nothing to publish: everything is already published.
    }

    @Override
    public void onRestore() {
        // Nothing to roll back either, and that is exactly the problem.
    }

    @Override
    public Map<String, Long> visible() {
        return store;
    }

    @Override
    public long visibleTotal() {
        long total = 0;
        for (long v : store.values()) {
            total += v;
        }
        return total;
    }

    @Override
    public String transactionSummary() {
        return writes + " direct writes, no transactions";
    }
}
