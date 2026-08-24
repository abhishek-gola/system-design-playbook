import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Synthetic but shaped like the real thing. Two properties matter and both are
 * deliberate.
 *
 * First, one tenant produces roughly seventy per cent of the traffic. Real
 * multi-tenant systems always have a whale, usually several, and a shard-key
 * argument that assumes uniform tenants is an argument about a system nobody
 * runs.
 *
 * Second, the synthetic clock advances by a millisecond per write, so the whole
 * run covers a couple of minutes of wall time and therefore lands inside a
 * single hourly time bucket. That is not a trick to make time-based sharding
 * look bad — it is what time-based sharding does. At any given moment, all the
 * writes are for the current bucket.
 */
public class WorkloadGenerator {

    private static final String[] METRICS = {
            "cpu.user", "cpu.system", "mem.used", "disk.io", "net.rx", "net.tx"
    };
    private static final String WHALE_TENANT = "acme";
    private static final String[] SMALL_TENANTS = {
            "bluebird", "corvid", "dunlin", "egret", "finch", "godwit", "heron"
    };
    private static final int WHALE_HOSTS = 400;
    private static final int SMALL_TENANT_HOSTS = 20;
    private static final int WHALE_SHARE_PERCENT = 70;

    // Fixed seed: the histograms in this demo must be the same on every machine,
    // otherwise the numbers in the README rot the first time someone runs it.
    private final Random random = new Random(42);

    private long clockMillis = 0L;

    public List<MetricWrite> generate(int count) {
        List<MetricWrite> writes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean whale = random.nextInt(100) < WHALE_SHARE_PERCENT;
            String tenant = whale ? WHALE_TENANT : SMALL_TENANTS[random.nextInt(SMALL_TENANTS.length)];
            int hostCount = whale ? WHALE_HOSTS : SMALL_TENANT_HOSTS;
            String host = tenant + "-host-" + random.nextInt(hostCount);
            String metric = METRICS[random.nextInt(METRICS.length)];

            clockMillis += 1;
            writes.add(new MetricWrite(metric, host, tenant, clockMillis, random.nextDouble() * 100.0));
        }
        return writes;
    }

    public String describe(int count) {
        return String.format("%,d points, %d metrics, %d whale hosts plus %d tenants of %d hosts, whale is ~%d%% of writes",
                count, METRICS.length, WHALE_HOSTS, SMALL_TENANTS.length, SMALL_TENANT_HOSTS, WHALE_SHARE_PERCENT);
    }
}
