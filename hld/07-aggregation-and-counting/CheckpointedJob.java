import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single-operator job with checkpointing and a crash, so the two sinks can be
 * put through identical failures.
 *
 * What is being modelled, and what a real Flink job does differently:
 *
 *  - The operator state is a plain TreeMap. In a real job this lives in a state
 *    backend (heap, or RocksDB once it stops fitting in memory) and is
 *    snapshotted asynchronously so the barrier does not stall the pipeline.
 *  - The checkpoint here is taken every N records at a single operator. A real
 *    checkpoint is a barrier injected at the source that flows through the
 *    whole graph; each operator snapshots the moment the barrier reaches it,
 *    and an operator with several inputs aligns the barriers by holding back
 *    the fast input until the slow one catches up. That alignment is where
 *    checkpointing shows up in your latency, and unaligned checkpoints are the
 *    trade you make when it hurts.
 *  - The offset is a list index. In a real job it is the Kafka offset, and it
 *    goes into the snapshot with the operator state. That pairing is the point:
 *    "which records have I consumed" and "what did they do to my state" have to
 *    be durable together, or replay is meaningless.
 *
 * The crash is a hard one. The process disappears. Anything the sink already
 * made visible stays visible; everything else comes back from the last complete
 * checkpoint and gets replayed.
 */
public final class CheckpointedJob {

    public record Report(String sinkName, long recordsInStream, long recordsReplayed,
                         int checkpointsCompleted, long sinkTotal, String transactions) {

        public long overcount() {
            return sinkTotal - recordsInStream;
        }
    }

    private final int checkpointEvery;

    public CheckpointedJob(int checkpointEvery) {
        this.checkpointEvery = checkpointEvery;
    }

    /**
     * @param crashAfter number of records to process before the process dies,
     *                   or -1 for a clean run.
     */
    public Report run(List<ClickEvent> stream, Sink sink, int crashAfter) {
        Map<String, Long> operatorState = new TreeMap<>();
        int offset = 0;

        Map<String, Long> checkpointedState = new TreeMap<>();
        int checkpointedOffset = 0;

        long nextCheckpointId = 1;
        int checkpointsCompleted = 0;

        long processed = 0;
        long replayed = 0;
        boolean crashed = false;

        while (offset < stream.size()) {

            if (!crashed && crashAfter >= 0 && processed == crashAfter) {
                crashed = true;
                replayed = processed - checkpointedOffset;
                sink.onRestore();
                operatorState = new TreeMap<>(checkpointedState);
                offset = checkpointedOffset;
                continue;
            }

            ClickEvent e = stream.get(offset);
            operatorState.merge(e.adId(), 1L, Long::sum);
            sink.write(e.adId(), 1L);
            offset++;
            processed++;

            if (offset % checkpointEvery == 0) {
                long id = nextCheckpointId++;
                sink.onBarrier(id);
                Map<String, Long> snapshot = new TreeMap<>(operatorState);
                int snapshotOffset = offset;
                // In a real job there is a gap here while the coordinator waits
                // for every operator to acknowledge. A checkpoint that never
                // completes never reaches the line below, and the sink's
                // pre-committed transaction stays invisible — which is the
                // whole safety property.
                sink.onCheckpointComplete(id);
                checkpointedState = snapshot;
                checkpointedOffset = snapshotOffset;
                checkpointsCompleted++;
            }
        }

        // A bounded source takes a final checkpoint on completion, so the tail
        // of the stream is committed rather than left in an open transaction.
        long finalId = nextCheckpointId;
        sink.onBarrier(finalId);
        sink.onCheckpointComplete(finalId);
        checkpointsCompleted++;

        return new Report(sink.name(), stream.size(), replayed, checkpointsCompleted,
                sink.visibleTotal(), sink.transactionSummary());
    }
}
