import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Streaming aggregation, end to end, with no dependencies and no clock.
 *
 * Run it with ./run.sh hld/07-aggregation-and-counting
 *
 * The order of the sections is the order you would answer the ad click
 * aggregator question in: get the counting off the write path, choose a window,
 * choose a notion of time, decide what happens to stragglers, decide what your
 * delivery guarantee actually is, and only then reach for approximation.
 */
public final class Demo {

    private static final long MINUTE = EventStream.MINUTE;

    public static void main(String[] args) {
        List<ClickEvent> arrived = EventStream.asArrived();
        List<ClickEvent> truth = EventStream.byEventTime();

        theStream(arrived, truth);
        threeWindows(arrived);
        eventTimeVersusProcessingTime(arrived);
        watermarksAndLateness(arrived, truth);
        checkpointsAndSinks(arrived);
        distinctCounts();
        heavyHitters();
        rateLimiting();

        System.out.println();
        System.out.println("Done. The sentence to take away: none of this is about counting.");
        System.out.println("It is about deciding when a count is finished, and what you do to the");
        System.out.println("answer when it turns out it was not.");
    }

    // ------------------------------------------------------------------
    // 1. The stream
    // ------------------------------------------------------------------

    private static void theStream(List<ClickEvent> arrived, List<ClickEvent> truth) {
        section("1. The stream");

        System.out.println("Ad clicks land on a Kafka topic partitioned by adId. Nothing increments a");
        System.out.println("row per click: the write path stays a sequential append, and the counting");
        System.out.println("happens downstream where contention is somebody else's problem.");
        System.out.println();
        System.out.println("  click -> Kafka (partitioned by adId) -> stream processor -> read store -> API");
        System.out.println("                        |");
        System.out.println("                        +-> raw events to object storage, for reprocessing");
        System.out.println();

        long span = truth.get(truth.size() - 1).eventTimeMs();
        System.out.println(fmt("%d events, spanning %s of event time, three bursts five minutes apart.",
                arrived.size(), mmss(span)));
        System.out.println();
        System.out.println("The first twelve as the consumer sees them. The arrow marks an event whose");
        System.out.println("timestamp is older than one already processed:");
        System.out.println();

        long maxSoFar = -1;
        long outOfOrder = 0;
        long worstLatenessMs = 0;
        for (int i = 0; i < arrived.size(); i++) {
            ClickEvent e = arrived.get(i);
            boolean late = e.eventTimeMs() < maxSoFar;
            if (late) {
                outOfOrder++;
                worstLatenessMs = Math.max(worstLatenessMs, maxSoFar - e.eventTimeMs());
            }
            if (i < 12) {
                System.out.println(fmt("   %-3d %-9s %-7s event time %s  %s",
                        i, e.adId(), e.userId(), mmss(e.eventTimeMs()), late ? "<- out of order" : ""));
            }
            maxSoFar = Math.max(maxSoFar, e.eventTimeMs());
        }

        System.out.println();
        System.out.println(fmt("%d of %d events arrive out of order. The worst is %s behind the",
                outOfOrder, arrived.size(), mmss(worstLatenessMs)));
        System.out.println("high-water mark, which is the number every design decision below hangs off.");
    }

    // ------------------------------------------------------------------
    // 2. Three windows over the same events
    // ------------------------------------------------------------------

    private static void threeWindows(List<ClickEvent> events) {
        section("2. Tumbling, sliding and session, over identical events");

        TumblingWindowAggregator tumbling = new TumblingWindowAggregator(MINUTE);
        SlidingWindowAggregator sliding = new SlidingWindowAggregator(3 * MINUTE, MINUTE);
        SessionWindowAggregator sessions = new SessionWindowAggregator(90_000L);
        for (ClickEvent e : events) {
            tumbling.add(e);
            sliding.add(e);
            sessions.add(e);
        }

        System.out.println("Tumbling, one minute. Fixed and non-overlapping, so every click is counted");
        System.out.println("exactly once and the counts add up to the event total:");
        System.out.println();
        printWindowCounts(tumbling.results(), MINUTE);
        System.out.println(fmt("   total across windows: %d (equals the %d events)",
                sum(tumbling.results()), events.size()));

        System.out.println();
        System.out.println("Sliding, three minutes, refreshed every minute. Each click lands in three");
        System.out.println("windows, so the totals deliberately do not add up to the event count:");
        System.out.println();
        printWindowCounts(sliding.results(), 3 * MINUTE);
        System.out.println(fmt("   total across windows: %d, which is roughly %dx the event count.",
                sum(sliding.results()), sliding.windowsPerEvent()));
        System.out.println("   That multiplier is your state size and your write amplification. A five");
        System.out.println("   minute window sliding every thirty seconds is 10x, and it is usually the");
        System.out.println("   answer to 'why is the job's state so large'.");

        System.out.println();
        System.out.println(fmt("Session, ninety second inactivity gap. %d sessions across %d users:",
                sessions.totalSessions(), sessions.sessions().size()));
        System.out.println();
        for (Map.Entry<String, List<SessionWindowAggregator.Session>> e : sessions.sessions().entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (SessionWindowAggregator.Session s : e.getValue()) {
                sb.append(fmt("  [%s..%s %d %s]", mmss(s.start), mmss(s.end), s.count, s.count == 1 ? "click" : "clicks"));
            }
            System.out.println(fmt("   %-8s %s", e.getKey(), sb.toString()));
        }
        System.out.println();
        System.out.println("The bursts are invisible in the tumbling output and obvious here. That is");
        System.out.println("the reason to reach for session windows: the boundaries come from user");
        System.out.println("behaviour rather than from the clock.");
    }

    // ------------------------------------------------------------------
    // 3. Event time versus processing time
    // ------------------------------------------------------------------

    private static void eventTimeVersusProcessingTime(List<ClickEvent> events) {
        section("3. Event time versus processing time");

        TumblingWindowAggregator byEvent = new TumblingWindowAggregator(MINUTE);
        ProcessingTimeWindower byProcessing = new ProcessingTimeWindower(MINUTE, 12_000L);
        for (ClickEvent e : events) {
            byEvent.add(e);
            byProcessing.add(e);
        }

        System.out.println("Same events, same one minute windows, two notions of time.");
        System.out.println();
        System.out.println("   event time (when the click happened):");
        printWindowCounts(byEvent.results(), MINUTE);
        System.out.println();
        System.out.println("   processing time (when it reached the operator):");
        printWindowCounts(byProcessing.results(), MINUTE);
        System.out.println();
        System.out.println("Event time shows what happened: three bursts with quiet minutes between");
        System.out.println("them. Processing time shows how fast the consumer was reading, which is a");
        System.out.println("fact about your infrastructure and not about your advertisers. Replay the");
        System.out.println("same topic after a backlog and the processing time numbers change while the");
        System.out.println("event time numbers do not. For anything you bill on, that settles it.");
    }

    // ------------------------------------------------------------------
    // 4. Watermarks, allowed lateness and the side output
    // ------------------------------------------------------------------

    private static void watermarksAndLateness(List<ClickEvent> arrived, List<ClickEvent> truth) {
        section("4. Watermarks, allowed lateness and the side output");

        long tolerance = 30_000L;
        long lateness = 60_000L;

        EventTimeWindower windower = new EventTimeWindower(MINUTE, tolerance, lateness);
        for (ClickEvent e : arrived) {
            windower.add(e);
        }
        windower.endOfStream();

        System.out.println(fmt("One minute windows, watermark = highest event time seen minus %s,",
                mmss(tolerance)));
        System.out.println(fmt("allowed lateness %s after the window closes.", mmss(lateness)));
        System.out.println();
        System.out.println("Every firing, in the order the job emitted them:");
        System.out.println();
        for (EventTimeWindower.Firing f : windower.firings()) {
            System.out.println(fmt("   window [%s..%s)  count %-3d %s",
                    mmss(f.windowStart()), mmss(f.windowEnd()), f.count(),
                    f.lateCorrection() ? "<- CORRECTION, a straggler landed inside the allowed lateness" : ""));
        }

        System.out.println();
        System.out.println("Side output (past window end plus allowed lateness, so the window state was");
        System.out.println("already dropped and there was nothing left to correct):");
        System.out.println();
        if (windower.sideOutput().isEmpty()) {
            System.out.println("   none");
        }
        for (EventTimeWindower.LateArrival late : windower.sideOutput()) {
            ClickEvent e = late.event();
            System.out.println(fmt("   %-9s %-7s event time %s, arrived when the watermark had reached %s",
                    e.adId(), e.userId(), mmss(e.eventTimeMs()), mmss(late.watermarkAtArrivalMs())));
        }
        System.out.println();
        System.out.println("The second of those is worth a second look. It was only four positions late");
        System.out.println("in arrival order, which the thirty second tolerance would normally absorb.");
        System.out.println("What killed it was the gap between bursts: the next event to arrive was from");
        System.out.println("three minutes later in event time, so the watermark jumped straight past its");
        System.out.println("window. A quiet stream punishes stragglers much harder than a busy one, and");
        System.out.println("that is not obvious until you have watched it happen.");
        System.out.println();
        System.out.println("Flink's default is to drop those silently. Routing them to a side output");
        System.out.println("instead is what lets you count them, alert when the rate moves, and hand");
        System.out.println("them to the batch job that reprocesses from object storage. Naming that as");
        System.out.println("the lambda architecture, rather than describing it, is the shortcut.");

        // Ground truth: the same windows computed over perfectly ordered events.
        TumblingWindowAggregator perfect = new TumblingWindowAggregator(MINUTE);
        for (ClickEvent e : truth) {
            perfect.add(e);
        }
        Map<Long, Long> streamed = windower.finalCounts();
        Map<Long, Long> exact = perfect.results();

        System.out.println();
        System.out.println("Streamed result against the same aggregation over perfectly ordered events:");
        System.out.println();
        Set<Long> allWindows = new TreeSet<>();
        allWindows.addAll(streamed.keySet());
        allWindows.addAll(exact.keySet());
        long lost = 0;
        for (long start : allWindows) {
            long got = streamed.getOrDefault(start, 0L);
            long want = exact.getOrDefault(start, 0L);
            if (got != want) {
                lost += want - got;
                System.out.println(fmt("   window [%s..%s)  streamed %d, actual %d  <- short by %d",
                        mmss(start), mmss(start + MINUTE), got, want, want - got));
            }
        }
        if (lost == 0) {
            System.out.println("   every window agrees");
        }
        System.out.println();
        System.out.println(fmt("Exactly %d %s missing, and %s the side output. That is",
                lost, lost == 1 ? "click is" : "clicks are", lost == 1 ? "it is the one in" : "they are the ones in"));
        System.out.println("the trade laid out honestly: the streaming path is fast and slightly");
        System.out.println("wrong, you know precisely how wrong and about which events, and the batch");
        System.out.println("path repairs it later. Raising the tolerance from 30s buys back that click");
        System.out.println("and costs every result 30 more seconds of staleness.");
    }

    // ------------------------------------------------------------------
    // 5. Checkpoints, and what exactly-once is actually made of
    // ------------------------------------------------------------------

    private static void checkpointsAndSinks(List<ClickEvent> events) {
        section("5. At-least-once versus effectively-once at the sink");

        int checkpointEvery = 20;
        int crashAfter = 35;

        System.out.println(fmt("%d records, a checkpoint every %d, and the process is killed after %d.",
                events.size(), checkpointEvery, crashAfter));
        System.out.println(fmt("The last complete checkpoint at that point covers %d records, so %d get",
                (crashAfter / checkpointEvery) * checkpointEvery,
                crashAfter - (crashAfter / checkpointEvery) * checkpointEvery));
        System.out.println("replayed from the source. Both sinks see identical input.");
        System.out.println();

        CheckpointedJob job = new CheckpointedJob(checkpointEvery);

        CheckpointedJob.Report clean = job.run(events, new TwoPhaseCommitSink(), -1);
        System.out.println(fmt("   no crash, two-phase-commit sink : total %d   (%s)",
                clean.sinkTotal(), clean.transactions()));

        CheckpointedJob.Report naive = job.run(events, new NaiveSink(), crashAfter);
        CheckpointedJob.Report twoPhase = job.run(events, new TwoPhaseCommitSink(), crashAfter);

        System.out.println();
        System.out.println(fmt("   crash, %s", naive.sinkName()));
        System.out.println(fmt("      records in stream %d, replayed after restore %d",
                naive.recordsInStream(), naive.recordsReplayed()));
        System.out.println(fmt("      sink total %d, overcounted by %d",
                naive.sinkTotal(), naive.overcount()));
        System.out.println(fmt("      %s", naive.transactions()));

        System.out.println();
        System.out.println(fmt("   crash, %s", twoPhase.sinkName()));
        System.out.println(fmt("      records in stream %d, replayed after restore %d",
                twoPhase.recordsInStream(), twoPhase.recordsReplayed()));
        System.out.println(fmt("      sink total %d, overcounted by %d",
                twoPhase.sinkTotal(), twoPhase.overcount()));
        System.out.println(fmt("      %s", twoPhase.transactions()));

        System.out.println();
        System.out.println(fmt("The naive sink is over by exactly the %d replayed records. Both jobs",
                naive.recordsReplayed()));
        System.out.println("recovered their own state correctly; the difference is entirely about what");
        System.out.println("the sink had already made visible.");
        System.out.println();
        System.out.println("The mechanism, in the order an interviewer wants to hear it:");
        System.out.println();
        System.out.println("   1. The coordinator injects a barrier at the source.");
        System.out.println("   2. The barrier flows through the graph with the records. Each operator");
        System.out.println("      snapshots its state the moment the barrier reaches it, and forwards");
        System.out.println("      the barrier on. An operator with two inputs aligns them: it holds back");
        System.out.println("      the fast input until the barrier arrives on the slow one, which is");
        System.out.println("      where checkpointing shows up in your latency.");
        System.out.println("   3. The sink pre-commits: durable, not yet visible.");
        System.out.println("   4. Every operator acknowledges, the coordinator declares the checkpoint");
        System.out.println("      complete, and only then does the sink commit.");
        System.out.println("   5. On restart, source offsets and operator state come back from the same");
        System.out.println("      checkpoint, and any transaction not covered by it is aborted.");
        System.out.println();
        System.out.println("Call it effectively-once, not exactly-once. Records are genuinely processed");
        System.out.println("more than once; what happens once is the effect on the outside world. If a");
        System.out.println("candidate cannot make that distinction, they have read about this rather");
        System.out.println("than run it.");
    }

    // ------------------------------------------------------------------
    // 6. HyperLogLog
    // ------------------------------------------------------------------

    private static void distinctCounts() {
        section("6. Distinct counts without keeping the keys");

        int distinct = 50_000;
        List<String> viewers = EventStream.viewerIds(distinct, 150_000);

        Set<String> exactSet = new HashSet<>(viewers);
        long exact = exactSet.size();

        System.out.println(fmt("%d viewer events, %d genuinely distinct viewers.", viewers.size(), exact));
        System.out.println("Counting them exactly means holding every id you have ever seen:");
        System.out.println(fmt("   HashSet: %d entries, at a conservative 60 bytes each that is about %d KB",
                exact, exact * 60 / 1024));
        System.out.println("   and it grows with the audience, per ad, per window.");
        System.out.println();

        int[] precisions = {10, 12, 14};
        System.out.println("   registers   memory   theoretical error   estimate   actual error");
        for (int p : precisions) {
            HyperLogLog hll = new HyperLogLog(p);
            for (String v : viewers) {
                hll.add(v);
            }
            long estimate = hll.estimate();
            double error = 100.0 * Math.abs(estimate - exact) / exact;
            System.out.println(fmt("   %-11d %-8s %-19s %-10d %.2f%%",
                    hll.registerCount(),
                    hll.sizeInBytes() / 1024 >= 1 ? (hll.sizeInBytes() / 1024) + " KB" : hll.sizeInBytes() + " B",
                    fmt("%.2f%%", hll.standardErrorPercent()),
                    estimate,
                    error));
        }

        System.out.println();
        System.out.println("Error is 1.04/sqrt(registers) and nothing else. It does not get worse as the");
        System.out.println("audience grows, which is the property worth stating: sixteen kilobytes gives");
        System.out.println("you the same one percent whether you are counting fifty thousand viewers or");
        System.out.println("fifty million.");
        System.out.println();

        // Mergeability, which is the reason this survives sharding.
        HyperLogLog shardA = new HyperLogLog(14);
        HyperLogLog shardB = new HyperLogLog(14);
        for (int i = 0; i < viewers.size(); i++) {
            if (i % 2 == 0) {
                shardA.add(viewers.get(i));
            } else {
                shardB.add(viewers.get(i));
            }
        }
        long a = shardA.estimate();
        long b = shardB.estimate();
        shardA.mergeFrom(shardB);
        System.out.println("Sharded over two nodes with overlapping audiences, then merged:");
        System.out.println(fmt("   shard A alone %d, shard B alone %d, naive sum %d", a, b, a + b));
        System.out.println(fmt("   merged sketch %d, against an actual %d", shardA.estimate(), exact));
        System.out.println();
        System.out.println("Adding the two shard counts double counts everyone who appears on both.");
        System.out.println("Merging takes the per-register maximum and gets the union right, with no");
        System.out.println("shuffle and no key movement. That is why every large distinct-count system");
        System.out.println("in production is built on sketches rather than on exact sets.");
    }

    // ------------------------------------------------------------------
    // 7. Count-Min Sketch and Top K
    // ------------------------------------------------------------------

    private static void heavyHitters() {
        section("7. Count-Min Sketch and Top K");

        List<String> views = EventStream.videoViews(5_000, 200_000);

        Map<String, Long> exact = new HashMap<>();
        for (String v : views) {
            exact.merge(v, 1L, Long::sum);
        }

        CountMinSketch sketch = new CountMinSketch(5, 2_048);
        TopKTracker topK = new TopKTracker(sketch, 10);
        for (String v : views) {
            topK.offer(v);
        }

        System.out.println(fmt("%d views across %d distinct videos, Zipf-shaped.", views.size(), exact.size()));
        System.out.println(fmt("Sketch: %d rows by %d counters = %d KB, fixed, regardless of how many",
                sketch.depth(), sketch.width(), sketch.sizeInBytes() / 1024));
        System.out.println(fmt("videos exist. An exact map needs %d entries and grows with the catalogue.",
                exact.size()));
        System.out.println();

        List<Map.Entry<String, Long>> ranked = new ArrayList<>(exact.entrySet());
        ranked.sort((x, y) -> {
            int byCount = Long.compare(y.getValue(), x.getValue());
            return byCount != 0 ? byCount : x.getKey().compareTo(y.getKey());
        });

        System.out.println("   video          actual   estimated   error");
        for (int i = 0; i < 5; i++) {
            Map.Entry<String, Long> e = ranked.get(i);
            long est = sketch.estimate(e.getKey());
            System.out.println(fmt("   %-14s %-8d %-11d +%.2f%%   (head)",
                    e.getKey(), e.getValue(), est, 100.0 * (est - e.getValue()) / e.getValue()));
        }
        for (int i = ranked.size() - 3; i < ranked.size(); i++) {
            Map.Entry<String, Long> e = ranked.get(i);
            long est = sketch.estimate(e.getKey());
            System.out.println(fmt("   %-14s %-8d %-11d +%.2f%%   (tail)",
                    e.getKey(), e.getValue(), est, 100.0 * (est - e.getValue()) / e.getValue()));
        }

        System.out.println();
        System.out.println("Every estimate is at or above the truth and never below it. The overcount is");
        System.out.println("roughly the same absolute amount for every key, which is noise on a head");
        System.out.println("item and a large relative error on a tail item. The sketch is accurate about");
        System.out.println("the things it exists for, and you should say so before being asked.");

        System.out.println();
        System.out.println(fmt("Top 10 from the tracker (%d candidates held, %d evictions over %d views):",
                topK.trackedKeys(), topK.evictions(), views.size()));
        System.out.println();
        System.out.println("   rank  sketch says              actually");
        List<TopKTracker.Entry> approx = topK.top();
        int agree = 0;
        for (int i = 0; i < approx.size(); i++) {
            TopKTracker.Entry e = approx.get(i);
            Map.Entry<String, Long> real = ranked.get(i);
            boolean same = e.key().equals(real.getKey());
            if (same) {
                agree++;
            }
            System.out.println(fmt("   %-5d %-14s %-9d %-14s %-9d %s",
                    i + 1, e.key(), e.estimatedCount(), real.getKey(), real.getValue(), same ? "" : "<- differs"));
        }
        System.out.println();
        System.out.println(fmt("%d of the top %d ranks match exactly.", agree, approx.size()));
        System.out.println("For a trending panel that is more than enough. If the ordering had to be");
        System.out.println("right, you would use the sketch to shortlist a few hundred candidates and");
        System.out.println("then count that shortlist exactly, which is cheap because it is small.");
    }

    // ------------------------------------------------------------------
    // 8. The distributed rate limiter
    // ------------------------------------------------------------------

    private static void rateLimiting() {
        section("8. The same pattern, small: a distributed rate limiter");

        int servers = 50;
        int requests = 5_000;
        long limit = 1_000;
        int syncEvery = 10;

        DistributedRateLimiter.Result strict = DistributedRateLimiter.strict(requests, limit);
        DistributedRateLimiter.Result fast = DistributedRateLimiter.localFastPath(servers, requests, limit, syncEvery);

        System.out.println(fmt("%d servers enforcing one limit of %d, against %d requests.",
                servers, limit, requests));
        System.out.println();
        System.out.println("   mode                              allowed   over limit   shared store calls");
        System.out.println(fmt("   %-33s %-9d %-12s %d",
                strict.mode(), strict.allowed(),
                strict.overshoot() + fmt(" (%.1f%%)", strict.overshootPercent()),
                strict.sharedStoreCalls()));
        System.out.println(fmt("   %-33s %-9d %-12s %d",
                fast.mode(), fast.allowed(),
                fast.overshoot() + fmt(" (%.1f%%)", fast.overshootPercent()),
                fast.sharedStoreCalls()));

        System.out.println();
        System.out.println(fmt("The fast path lets %d requests through above the limit and cuts the",
                fast.overshoot()));
        System.out.println(fmt("shared store from %d calls to %d. That is the entire trade, and it is",
                strict.sharedStoreCalls(), fast.sharedStoreCalls()));
        System.out.println("the answer to 'where does the counter live when fifty servers enforce one");
        System.out.println("limit'. Turning syncEvery down shrinks the overshoot and raises the call");
        System.out.println("volume; there is no setting that gives you both.");
        System.out.println();
        System.out.println("Whether that overshoot is acceptable is a product question, not an");
        System.out.println("engineering one. A public API's thousand-per-minute is a fairness measure");
        System.out.println("and nobody is harmed by 1040. A payment provider's contractual limit is a");
        System.out.println("promise, and you pay for the round trip on every request.");
        System.out.println();
        System.out.println("The algorithms underneath — token bucket, sliding window log, fixed window");
        System.out.println("counter — are implemented properly one level down, in lld/02-strategy,");
        System.out.println("behind one interface with an injected clock. Same problem, two zoom levels.");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(74));
        System.out.println(title);
        System.out.println("=".repeat(74));
        System.out.println();
    }

    private static void printWindowCounts(Map<Long, Long> counts, long sizeMs) {
        for (Map.Entry<Long, Long> e : counts.entrySet()) {
            System.out.println(fmt("   [%s..%s)  %s %d",
                    mmss(e.getKey()), mmss(e.getKey() + sizeMs), bar(e.getValue()), e.getValue()));
        }
    }

    private static String bar(long n) {
        return "#".repeat((int) Math.min(n, 40));
    }

    private static long sum(Map<Long, Long> counts) {
        long total = 0;
        for (long v : counts.values()) {
            total += v;
        }
        return total;
    }

    /** Synthetic timestamps as minutes and seconds, so the output reads like a timeline. */
    private static String mmss(long ms) {
        return fmt("%d:%02d", ms / 60_000, (ms % 60_000) / 1000);
    }

    /** Locale.UK everywhere, so number formatting is identical on every machine. */
    private static String fmt(String format, Object... args) {
        return String.format(Locale.UK, format, args);
    }
}
