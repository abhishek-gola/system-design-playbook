import java.util.Map;

/**
 * Where aggregated results leave the job.
 *
 * The lifecycle here is the important part, and it is the four calls that
 * candidates cannot name when they say "exactly once":
 *
 *   write               a record is produced by the operator
 *   onBarrier           a checkpoint barrier has flowed through to the sink;
 *                       the sink prepares, but does not publish, everything it
 *                       has buffered since the last barrier
 *   onCheckpointComplete the coordinator has heard from every operator in the
 *                       graph and declared the checkpoint durable; only now may
 *                       the sink publish
 *   onRestore           the job died and came back from the last complete
 *                       checkpoint; anything prepared but not published must be
 *                       thrown away
 *
 * Two implementations sit behind this: one that ignores the lifecycle entirely
 * and one that uses it. Running the same crash through both is the clearest way
 * to see what the guarantee is actually made of.
 */
public interface Sink {

    String name();

    void write(String key, long delta);

    void onBarrier(long checkpointId);

    void onCheckpointComplete(long checkpointId);

    void onRestore();

    /** What a reader querying the sink right now would see. */
    Map<String, Long> visible();

    long visibleTotal();

    String transactionSummary();
}
