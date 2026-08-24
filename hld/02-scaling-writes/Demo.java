import java.util.List;

/**
 * Route the same stream of metric writes across the same sixteen shards under
 * three different key choices, then show what batching does to the number of
 * database round trips.
 *
 * The reason to run this rather than read it: the timestamp and tenant keys are
 * both defensible in a sentence, and both are wrong here, and the histogram is
 * the fastest way to feel why. Once you have seen one shard at a hundred per
 * cent you stop suggesting time-based keys in interviews.
 */
public class Demo {

    private static final int WRITES = 100_000;
    private static final int SHARDS = 16;

    public static void main(String[] args) {
        WorkloadGenerator generator = new WorkloadGenerator();
        List<MetricWrite> writes = generator.generate(WRITES);

        System.out.println("=== Scaling writes: shard keys and batching ===");
        System.out.println("workload: " + generator.describe(WRITES));
        System.out.println("shards:   " + SHARDS);

        distribution(ShardKey.METRIC_AND_HOST, writes,
                "spreads evenly, and one query for one metric on one host stays on one shard.");
        distribution(ShardKey.TIMESTAMP, writes,
                "every write lands in the bucket holding 'now'. A hot partition by construction.");
        distribution(ShardKey.TENANT, writes,
                "clean isolation until the whale arrives. One key cannot be split by rebalancing.");

        batching();
        closing();
    }

    private static void distribution(ShardKey key, List<MetricWrite> writes, String verdict) {
        ShardRouter router = new ShardRouter(key, SHARDS);
        router.routeAll(writes);

        System.out.println();
        System.out.println("shard by " + key.label());
        router.printReport();
        System.out.println("    " + verdict);
    }

    /**
     * The other half of write scaling, and the cheaper half. Nothing here is
     * sharded differently — the same rows go to the same places — the only
     * change is how many network round trips it takes to get them there.
     */
    private static void batching() {
        BatchWriter writer = new BatchWriter();
        int rows = 100_000;

        System.out.println();
        System.out.printf("batching %,d small inserts (cost model: 1ms per round trip, 10us per row)%n", rows);
        System.out.println("    batch size   round trips   simulated time");
        for (int batchSize : new int[]{1, 10, 100, 1_000, 10_000}) {
            BatchWriter.Result result = writer.write(rows, batchSize);
            System.out.printf("    %,10d   %,11d   %8.2fs%n",
                    result.batchSize(), result.roundTrips(), result.seconds());
        }
        System.out.println("    the win is almost entirely in the first two steps. Past a thousand rows");
        System.out.println("    the per-row cost dominates and bigger batches only cost you memory,");
        System.out.println("    lock duration, and a coarser unit of retry.");
    }

    private static void closing() {
        System.out.println();
        System.out.println("The sentence this is all for: \"a good shard key spreads writes evenly and");
        System.out.println("keeps the rows one query needs on few nodes. Those pull against each other,");
        System.out.println("and here metric plus host satisfies both — timestamp fails the first and");
        System.out.println("tenant fails it the moment one customer gets large.\"");
    }
}
