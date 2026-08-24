import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * The synthetic stream. This stands in for a Kafka topic: byEventTime() is the
 * order things actually happened in the world, asArrived() is the order the
 * consumer sees them. No real system ever hands you the first one.
 *
 * Everything here is driven by a fixed seed and synthetic timestamps, never the
 * wall clock, so the demo prints the same numbers on every machine.
 */
public final class EventStream {

    public static final long MINUTE = 60_000L;

    private EventStream() {
    }

    /**
     * Three bursts of twenty clicks, five minutes apart. Bursts matter: a flat
     * stream makes event time and processing time look identical, and the whole
     * point is that they are not.
     */
    public static List<ClickEvent> byEventTime() {
        Random rnd = new Random(42);
        String[] ads = {"ad-alpha", "ad-beta", "ad-gamma"};
        List<ClickEvent> out = new ArrayList<>();
        for (int burst = 0; burst < 3; burst++) {
            long t = burst * 5 * MINUTE;
            for (int i = 0; i < 20; i++) {
                t += 5_000L + rnd.nextInt(4) * 1_000L;
                String ad = ads[rnd.nextInt(ads.length)];
                String user = "user-" + (1 + rnd.nextInt(4));
                out.add(new ClickEvent(ad, user, t));
            }
        }
        return out;
    }

    /**
     * The same events in the order a consumer would see them.
     *
     * Two separate causes of disorder, and they need different answers:
     *
     *  1. Ordinary network jitter. Every event can slip a few positions. This
     *     is what a watermark delay is for — you wait a bounded amount and you
     *     get almost everything.
     *
     *  2. A client that was offline and flushed its buffer minutes later. No
     *     sane watermark delay covers this, and pretending otherwise is how you
     *     end up holding window state forever. This is what side outputs are
     *     for.
     *
     * Both are injected here deliberately rather than left to chance, so both
     * branches of the watermark logic are exercised every run.
     */
    public static List<ClickEvent> asArrived() {
        List<ClickEvent> ordered = byEventTime();
        int n = ordered.size();

        // Cause 1: give every event a jitter of nought to four positions and
        // stable-sort on the result. Bounded disorder, which is exactly the
        // assumption a watermark encodes.
        Random rnd = new Random(7);
        final int[] key = new int[n];
        for (int i = 0; i < n; i++) {
            key[i] = i + rnd.nextInt(5);
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> key[a] != key[b] ? Integer.compare(key[a], key[b]) : Integer.compare(a, b));

        List<ClickEvent> arrived = new ArrayList<>();
        for (int i : order) {
            arrived.add(ordered.get(i));
        }

        // Cause 2a: a straggler that lands after its window has fired but
        // before the state was dropped. Allowed lateness catches this one and
        // the window re-fires with a corrected count.
        ClickEvent moderatelyLate = firstAtOrAfter(ordered, 5 * MINUTE);
        moveToAfterHighWatermark(arrived, moderatelyLate, 400_000L);

        // Cause 2b: an event from the first burst that turns up at the very end
        // of the stream. Its window state is long gone. Nothing recovers this
        // on the streaming path; it goes to the side output and the batch job
        // fixes it later.
        ClickEvent hopelesslyLate = ordered.get(2);
        arrived.remove(hopelesslyLate);
        arrived.add(hopelesslyLate);

        return arrived;
    }

    /** The distinct-count workload: a known cardinality, so we can measure the sketch's error honestly. */
    public static List<String> viewerIds(int distinct, int repeats) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < distinct; i++) {
            ids.add("viewer-" + i);
        }
        Random rnd = new Random(5);
        for (int i = 0; i < repeats; i++) {
            ids.add("viewer-" + rnd.nextInt(distinct));
        }
        return ids;
    }

    /**
     * The heavy-hitter workload. Real view traffic is Zipf-shaped: a handful of
     * videos take most of the traffic and a very long tail takes the rest.
     * Cubing a uniform draw is a cheap stand-in that produces the same shape,
     * which is all the sketch demo needs.
     */
    public static List<String> videoViews(int distinctVideos, int views) {
        Random rnd = new Random(11);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < views; i++) {
            double u = rnd.nextDouble();
            int rank = (int) Math.floor(u * u * u * distinctVideos);
            out.add("video-" + Math.min(rank, distinctVideos - 1));
        }
        return out;
    }

    private static ClickEvent firstAtOrAfter(List<ClickEvent> ordered, long eventTimeMs) {
        for (ClickEvent e : ordered) {
            if (e.eventTimeMs() >= eventTimeMs) {
                return e;
            }
        }
        return ordered.get(ordered.size() - 1);
    }

    /**
     * Reposition one event so it arrives just after the stream's maximum event
     * time crosses a chosen point. That fixes where the watermark will be when
     * the event lands, which is the only way to make "late but inside the
     * allowed lateness" reproducible rather than lucky.
     */
    private static void moveToAfterHighWatermark(List<ClickEvent> arrived, ClickEvent event, long targetMaxEventTime) {
        arrived.remove(event);
        long max = -1;
        for (int i = 0; i < arrived.size(); i++) {
            max = Math.max(max, arrived.get(i).eventTimeMs());
            if (max >= targetMaxEventTime) {
                arrived.add(i + 1, event);
                return;
            }
        }
        arrived.add(event);
    }
}
