import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hash the shard key, take the modulus, count what lands where.
 *
 * Modulus is the simplest possible placement and it is deliberately used here,
 * because the thing being demonstrated is key choice rather than placement. Be
 * ready for the follow-up though: modulus means adding a shard remaps almost
 * every key, so a real system uses consistent hashing with virtual nodes and
 * moves roughly 1/N of the data when N changes. Consistent hashing fixes
 * resharding. It does not fix a bad key — if seventy per cent of your writes
 * share one key, every placement scheme in the world puts them on one node.
 *
 * The distinct-key count per shard is tracked because it answers the question
 * that follows a hot shard: can this be rebalanced? A shard holding one key
 * cannot. That is the difference between a capacity problem and a design
 * problem.
 */
public class ShardRouter {

    private static final int BAR_WIDTH = 28;

    private final ShardKey shardKey;
    private final int shardCount;
    private final long[] writesPerShard;
    private final List<Set<String>> keysPerShard;

    public ShardRouter(ShardKey shardKey, int shardCount) {
        this.shardKey = shardKey;
        this.shardCount = shardCount;
        this.writesPerShard = new long[shardCount];
        this.keysPerShard = new ArrayList<>(shardCount);
        for (int i = 0; i < shardCount; i++) {
            keysPerShard.add(new HashSet<>());
        }
    }

    public void routeAll(List<MetricWrite> writes) {
        for (MetricWrite write : writes) {
            String key = shardKey.keyFor(write);
            int shard = Math.floorMod(key.hashCode(), shardCount);
            writesPerShard[shard]++;
            keysPerShard.get(shard).add(key);
        }
    }

    public void printReport() {
        long total = 0;
        long busiest = 0;
        int busiestShard = 0;
        int idleShards = 0;
        for (int i = 0; i < shardCount; i++) {
            total += writesPerShard[i];
            if (writesPerShard[i] > busiest) {
                busiest = writesPerShard[i];
                busiestShard = i;
            }
            if (writesPerShard[i] == 0) {
                idleShards++;
            }
        }

        for (int i = 0; i < shardCount; i++) {
            long count = writesPerShard[i];
            int bar = busiest == 0 ? 0 : (int) Math.round((double) BAR_WIDTH * count / busiest);
            System.out.printf("    shard %2d  %-28s %,9d  %5.1f%%  %,6d keys%n",
                    i, "#".repeat(bar), count, 100.0 * count / total, keysPerShard.get(i).size());
        }

        double average = (double) total / shardCount;
        System.out.printf("    busiest shard %d holds %.1f%% of writes across %,d distinct key%s%n",
                busiestShard,
                100.0 * busiest / total,
                keysPerShard.get(busiestShard).size(),
                keysPerShard.get(busiestShard).size() == 1 ? "" : "s");
        System.out.printf("    imbalance: busiest shard is %.1fx the average, %d shard%s idle%n",
                busiest / average, idleShards, idleShards == 1 ? "" : "s");
    }
}
