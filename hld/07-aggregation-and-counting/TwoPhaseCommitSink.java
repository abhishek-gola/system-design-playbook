import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The sink half of end-to-end exactly-once.
 *
 * Nothing here is exotic. Records accumulate in an open transaction. When a
 * checkpoint barrier reaches the sink, that transaction is pre-committed:
 * durable enough that it can be committed later without the sink's memory, but
 * not yet visible to anybody reading the sink. Only when the coordinator tells
 * the sink the whole checkpoint completed does it commit and become visible.
 *
 * The reason this gives you an end-to-end guarantee is the ordering. A
 * checkpoint completes only if every operator in the graph acknowledged its
 * snapshot, so the sink commits transaction N only when the source offsets and
 * all the operator state for the same N are durable. Restart from that
 * checkpoint and the replayed records go into a fresh transaction; the aborted
 * one never becomes visible, so nothing is counted twice.
 *
 * The costs to name out loud, because an interviewer will go looking for them:
 *
 *  - results are only visible at checkpoint boundaries, so your checkpoint
 *    interval becomes your end-to-end latency floor. Ten-second checkpoints
 *    mean a dashboard that is up to ten seconds behind, and that is a product
 *    decision, not an engineering one.
 *  - the destination must actually support transactions or an equivalent
 *    two-phase protocol. Kafka does, JDBC does, S3 does via multipart uploads
 *    committed at the end. A plain HTTP POST to somebody's API does not, and
 *    the honest answer there is idempotency keys instead.
 *  - a pre-committed transaction whose commit never arrives blocks. Every
 *    two-phase commit has this failure mode and pretending otherwise is worse
 *    than naming it.
 */
public final class TwoPhaseCommitSink implements Sink {

    private final Map<String, Long> committed = new TreeMap<>();
    private final Map<Long, Map<String, Long>> preCommitted = new LinkedHashMap<>();
    private Map<String, Long> openTransaction = new TreeMap<>();

    private int commits = 0;
    private int aborts = 0;

    @Override
    public String name() {
        return "two-phase-commit sink (commits only on checkpoint completion)";
    }

    @Override
    public void write(String key, long delta) {
        openTransaction.merge(key, delta, Long::sum);
    }

    @Override
    public void onBarrier(long checkpointId) {
        // Phase one. The transaction is closed and made recoverable, but a
        // reader of the sink still cannot see any of it.
        preCommitted.put(checkpointId, openTransaction);
        openTransaction = new TreeMap<>();
    }

    @Override
    public void onCheckpointComplete(long checkpointId) {
        // Phase two, and only after every other operator has acknowledged.
        Map<String, Long> transaction = preCommitted.remove(checkpointId);
        if (transaction == null) {
            return;
        }
        for (Map.Entry<String, Long> e : transaction.entrySet()) {
            committed.merge(e.getKey(), e.getValue(), Long::sum);
        }
        commits++;
    }

    @Override
    public void onRestore() {
        // Everything pre-committed after the last complete checkpoint is
        // abandoned, along with whatever was in the open transaction. Those
        // records are about to be replayed from the source, and letting the old
        // transaction survive is exactly how you would double-count.
        aborts += preCommitted.size();
        if (!openTransaction.isEmpty()) {
            aborts++;
        }
        preCommitted.clear();
        openTransaction = new TreeMap<>();
    }

    @Override
    public Map<String, Long> visible() {
        return committed;
    }

    @Override
    public long visibleTotal() {
        long total = 0;
        for (long v : committed.values()) {
            total += v;
        }
        return total;
    }

    @Override
    public String transactionSummary() {
        return commits + " transactions committed, " + aborts + " aborted on restore";
    }
}
