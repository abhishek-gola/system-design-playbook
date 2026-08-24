import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The bottom rung of the ladder: check-then-act, and the same code with the
 * check and the act fused into one atomic operation.
 *
 * Both versions store seats in a ConcurrentHashMap, which is deliberate. The
 * bug in the naive version is not a thread-unsafe data structure - people
 * reach for "use a concurrent map" as if that were the fix, and it isn't. The
 * bug is that reading the seat and claiming the seat are two separate steps,
 * and another thread gets to run in the gap between them. Swapping the map for
 * a database row changes nothing: SELECT then UPDATE has the same gap, it is
 * just wider because there is a network in it.
 *
 * Each round is a fresh seat with several buyers going for it at once. We run
 * many rounds and report how many of them oversold, because a single round is
 * a coin toss and an aggregate is evidence.
 */
public final class SeatRace {

    private SeatRace() {
    }

    /** How many rounds ended with more than one buyer believing they had won. */
    public static int naiveOversoldRounds(int rounds, int buyersPerRound) throws InterruptedException {
        return runRounds(rounds, buyersPerRound, false);
    }

    /** Same experiment, with putIfAbsent doing the check and the act together. */
    public static int casOversoldRounds(int rounds, int buyersPerRound) throws InterruptedException {
        return runRounds(rounds, buyersPerRound, true);
    }

    private static int runRounds(int rounds, int buyersPerRound, boolean atomic) throws InterruptedException {
        int oversoldRounds = 0;
        ExecutorService pool = Executors.newFixedThreadPool(buyersPerRound);
        try {
            for (int round = 0; round < rounds; round++) {
                // Stands in for one row in a seats table. Empty means unsold.
                final Map<String, String> seats = new ConcurrentHashMap<>();
                final AtomicInteger believedTheyWon = new AtomicInteger();

                // Every buyer waits on the same starting gun, so they arrive
                // together rather than in the order the pool happened to schedule
                // them. Without this the race almost never reproduces.
                final CountDownLatch startingGun = new CountDownLatch(1);
                final CountDownLatch finished = new CountDownLatch(buyersPerRound);

                for (int i = 0; i < buyersPerRound; i++) {
                    final String buyer = "buyer-" + i;
                    pool.execute(() -> {
                        try {
                            startingGun.await();
                            if (atomic) {
                                // One operation. The map decides the winner, and
                                // it can only decide once. This is the same shape
                                // as INSERT ... ON CONFLICT DO NOTHING, or an
                                // UPDATE with a WHERE clause on the current value.
                                if (seats.putIfAbsent("14A", buyer) == null) {
                                    believedTheyWon.incrementAndGet();
                                }
                            } else {
                                if (seats.get("14A") == null) {          // the check
                                    widenTheWindow();                    // the gap a real network gives you for free
                                    seats.put("14A", buyer);             // the act
                                    believedTheyWon.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    });
                }

                startingGun.countDown();
                finished.await();
                if (believedTheyWon.get() > 1) {
                    oversoldRounds++;
                }
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
        return oversoldRounds;
    }

    /**
     * In-process the gap between the check and the act is a handful of
     * nanoseconds, so we stretch it. In your actual system you do not need to
     * stretch anything: the gap is a round trip to the database, and then the
     * user's payment step, and it is enormous.
     */
    private static void widenTheWindow() {
        for (int i = 0; i < 200; i++) {
            Thread.onSpinWait();
        }
    }
}
