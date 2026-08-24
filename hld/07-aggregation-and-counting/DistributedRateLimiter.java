import java.util.Random;

/**
 * Rate limiting is this whole folder in miniature: a counter, a window, and a
 * decision about where the counter lives.
 *
 * The algorithms themselves — token bucket, sliding window log, fixed window
 * counter — are implemented properly one level down, in
 * lld/02-strategy, behind a single interface with an injected clock. That is
 * the LLD answer. This file is the HLD answer, and it is a different question:
 * fifty API servers are enforcing one limit, so where does the counter live?
 *
 * Two ends of the spectrum, both simulated below.
 *
 * Strict: every request round-trips to a shared counter, typically Redis
 * running a small Lua script so the read, the check and the increment are one
 * atomic operation. Exactly correct, and it costs one network hop per request
 * plus a hard dependency on Redis being up. When Redis is down you now have to
 * decide, in advance and in writing, whether the gateway fails open or fails
 * closed. Fail open and an outage becomes a free-for-all; fail closed and a
 * Redis blip takes the whole product down. For rate limiting the usual answer
 * is fail open, because the limiter protects you from load rather than from
 * fraud — but say which and why.
 *
 * Local fast path: each server admits a small batch on its own view and syncs
 * with the shared counter periodically. Almost all requests are decided in
 * memory, the shared store sees a fraction of the traffic, and in exchange the
 * limit is enforced approximately. Servers act on stale views, so the true
 * total overshoots.
 *
 * Which to pick depends entirely on what the limit means. A public API's
 * "1000 requests per minute" is a fairness measure and nobody is harmed by
 * 1040. A payment provider's contractual limit is a promise, and you pay for
 * the round trip. Naming that distinction, rather than declaring one design
 * correct, is what the question is testing.
 */
public final class DistributedRateLimiter {

    public record Result(String mode, long allowed, long rejected, long limit, long sharedStoreCalls) {

        public long overshoot() {
            return Math.max(0, allowed - limit);
        }

        public double overshootPercent() {
            return 100.0 * overshoot() / limit;
        }
    }

    private DistributedRateLimiter() {
    }

    /** Every request checks and increments the shared counter atomically. */
    public static Result strict(int requests, long limit) {
        long used = 0;
        long allowed = 0;
        long rejected = 0;
        long calls = 0;

        for (int i = 0; i < requests; i++) {
            calls++;
            if (used < limit) {
                used++;
                allowed++;
            } else {
                rejected++;
            }
        }
        return new Result("strict shared counter", allowed, rejected, limit, calls);
    }

    /**
     * Each server decides locally and pushes its tally to the shared counter
     * every syncEvery admissions.
     *
     * The overshoot comes from two places at once, and it is worth separating
     * them when you explain it. A server can be holding up to syncEvery
     * admissions it has not reported yet, and its view of the global total was
     * taken before every other server's unreported batch. Fifty servers each
     * holding a batch of ten is up to five hundred requests the shared counter
     * has never heard of.
     */
    public static Result localFastPath(int servers, int requests, long limit, int syncEvery) {
        long[] unreported = new long[servers];
        long[] globalAtLastSync = new long[servers];

        long globalUsed = 0;
        long allowed = 0;
        long rejected = 0;
        long calls = 0;

        // A deterministic stand-in for a load balancer spreading requests.
        Random rnd = new Random(99);

        for (int i = 0; i < requests; i++) {
            int server = rnd.nextInt(servers);

            long believedTotal = globalAtLastSync[server] + unreported[server];
            if (believedTotal >= limit) {
                rejected++;
                continue;
            }

            unreported[server]++;
            allowed++;

            if (unreported[server] >= syncEvery) {
                globalUsed += unreported[server];
                unreported[server] = 0;
                globalAtLastSync[server] = globalUsed;
                calls++;
            }
        }
        return new Result("local fast path, sync every " + syncEvery, allowed, rejected, limit, calls);
    }
}
