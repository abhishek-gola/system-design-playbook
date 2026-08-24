/**
 * A cost model, not a benchmark, and you should say so if you use these numbers
 * out loud. Every insert path costs two different things: a fixed price per
 * round trip to the database — network hop, parse, transaction commit, fsync —
 * and a variable price per row for marshalling and index maintenance.
 *
 * Batching attacks the fixed cost only. That is why the first two orders of
 * magnitude are enormous and the next two barely register: once the per-row
 * cost dominates, larger batches buy you nothing and start costing you memory,
 * lock duration, and a coarser retry granularity. Knowing where that knee sits
 * is the difference between "batch the writes" as a slogan and as a decision.
 */
public class BatchWriter {

    /** Round trip plus commit. Half a millisecond is a datacentre hop; call the rest the database's own work. */
    private static final long ROUND_TRIP_MICROS = 1_000L;

    /** Marshalling the row and maintaining its indexes. */
    private static final long PER_ROW_MICROS = 10L;

    public record Result(int rows, int batchSize, int roundTrips, long totalMicros) {

        public double seconds() {
            return totalMicros / 1_000_000.0;
        }
    }

    public Result write(int rows, int batchSize) {
        int roundTrips = (rows + batchSize - 1) / batchSize;      // ceiling division
        long totalMicros = (long) roundTrips * ROUND_TRIP_MICROS + (long) rows * PER_ROW_MICROS;
        return new Result(rows, batchSize, roundTrips, totalMicros);
    }
}
