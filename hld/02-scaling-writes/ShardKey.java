/**
 * The three shard keys a candidate reaches for on a metrics problem, and the
 * reason this folder has code in it at all — the difference between them is
 * obvious in a histogram and easy to hand-wave in prose.
 *
 * Note what a shard key is: a function from a row to a string, which is then
 * hashed to a shard number. Nothing more. All the design content is in the
 * choice of what goes into that string.
 */
public enum ShardKey {

    /**
     * Spreads evenly, because there are thousands of metric-and-host pairs and
     * no single one dominates. It also keeps the rows a single query needs
     * together: "cpu.user on web-07 for the last hour" is one shard.
     */
    METRIC_AND_HOST("metric + host"),

    /**
     * Time-bucketed, which is how time-series shards are often described. Every
     * write in the universe goes to the bucket holding "now", so one shard takes
     * all of the ingest and the rest hold history. A hot partition by
     * construction, and the mistake interviewers watch for.
     */
    TIMESTAMP("timestamp"),

    /**
     * Natural isolation and easy per-customer operations, until one customer is
     * fifty times the size of the others. Then that customer is a shard, and no
     * amount of rebalancing helps because the key cannot be split any further.
     */
    TENANT("tenant");

    /** One bucket per hour, which is the usual granularity for time-based shards. */
    private static final long BUCKET_MILLIS = 60L * 60L * 1000L;

    private final String label;

    ShardKey(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String keyFor(MetricWrite write) {
        return switch (this) {
            case METRIC_AND_HOST -> write.metric() + "|" + write.host();
            case TIMESTAMP -> "hour-" + (write.timestampMillis() / BUCKET_MILLIS);
            case TENANT -> write.tenant();
        };
    }
}
