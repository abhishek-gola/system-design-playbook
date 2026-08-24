import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Demo {

    private static final int THREADS = 40;
    private static final String SEAT = "14A";

    public static void main(String[] args) throws Exception {
        theRace();
        compareAndSet();
        perSeatLocking();
        theDeadlock();
        holdThenConfirm();
        volatileIsNotAtomic();
    }

    // ------------------------------------------------------------------

    private static void theRace() throws Exception {
        System.out.println("== The race: " + THREADS + " threads, one seat, check-then-act ==");
        NaiveBooking booking = new NaiveBooking();
        int winners = raceFor(user -> booking.book(SEAT, user));

        System.out.println("  threads that believed they had won seat " + SEAT + ": " + winners);
        System.out.println("  the seat is recorded as belonging to: " + booking.ownerOf(SEAT));
        if (winners > 1) {
            System.out.println("  " + winners + " customers, one seat. That is the bug.");
        } else {
            System.out.println("  It happened to come out as one on this run. The bug is still");
            System.out.println("  there — an intermittent race is worse than a reliable one,");
            System.out.println("  because it reaches production before it reaches your tests.");
        }
        System.out.println();
    }

    private static void compareAndSet() throws Exception {
        System.out.println("== Compare-and-set: one atomic call instead of two steps ==");
        CasBooking booking = new CasBooking();
        int winners = raceFor(user -> booking.book(SEAT, user));

        System.out.println("  winners: " + winners + "   owner: " + booking.ownerOf(SEAT));
        System.out.println("  No lock was taken and nothing blocked. Losers found out");
        System.out.println("  immediately rather than queueing to be told no.");
        System.out.println();
    }

    private static void perSeatLocking() throws Exception {
        System.out.println("== Per-seat locking: correct, and it serialises only that seat ==");
        LockBooking booking = new LockBooking();
        int winners = raceFor(user -> booking.book(SEAT, user));

        System.out.println("  winners: " + winners + "   owner: " + booking.ownerOf(SEAT));
        System.out.println("  synchronized(this) would be equally correct and would turn a");
        System.out.println("  500-seat cinema into a queue of one. Lock the smallest thing");
        System.out.println("  that makes the invariant true.");
        System.out.println();
    }

    private static void theDeadlock() throws Exception {
        System.out.println("== Group booking: two seats, two threads, opposite order ==");

        System.out.println("  without sorting:");
        int unsorted = groupRace(false);
        System.out.println("    groups that succeeded: " + unsorted + " of 2");
        if (unsorted < 2) {
            System.out.println("    At least one timed out on tryLock — that is a deadlock");
            System.out.println("    caught by the safety net rather than a hang. Without the");
            System.out.println("    timeout, both threads would still be waiting.");
        } else {
            System.out.println("    They missed each other on this run. The cycle is still");
            System.out.println("    reachable — run it again and it will bite.");
        }

        System.out.println("  with a fixed global order:");
        int sorted = groupRace(true);
        System.out.println("    groups that succeeded: " + sorted + " of 2");
        System.out.println("    One got both seats, the other found them taken. No cycle can");
        System.out.println("    form when everyone acquires in the same order, so there is");
        System.out.println("    nothing for tryLock to rescue.");
        System.out.println();
    }

    private static void holdThenConfirm() {
        System.out.println("== Hold, take payment, confirm ==");
        ManualTicker clock = new ManualTicker(0);
        HoldThenConfirm booking = new HoldThenConfirm(clock);

        String aliceToken = booking.hold(SEAT, "alice");
        String bobToken = booking.hold(SEAT, "bob");
        System.out.println("  alice holds: " + aliceToken);
        System.out.println("  bob holds:   " + bobToken + "   (seat is taken)");

        System.out.println("  ...alice spends four minutes on the payment page...");
        clock.advanceMinutes(4);
        System.out.println("  alice confirms: " + booking.confirm(SEAT, aliceToken));
        System.out.println("  owner: " + booking.confirmedOwner(SEAT));
        System.out.println("  Nothing was locked while a third party held the request.");

        System.out.println();
        System.out.println("  Now an abandoned hold on a different seat:");
        String carolToken = booking.hold("14B", "carol");
        System.out.println("    carol holds 14B: " + carolToken);
        System.out.println("    dave tries:      " + booking.hold("14B", "dave") + "  (held)");

        clock.advanceMinutes(11);
        System.out.println("    ...eleven minutes pass, carol never paid...");
        System.out.println("    dave tries again: " + booking.hold("14B", "dave"));
        System.out.println("    carol comes back and confirms: " + booking.confirm("14B", carolToken));
        System.out.println("    Her token no longer matches the live hold, so a stale payment");
        System.out.println("    cannot claim a seat somebody else now owns. That check is the");
        System.out.println("    same idea as a fencing token in a distributed lock.");

        System.out.println();
        System.out.println("    active holds before sweep: " + booking.activeHolds());
        clock.advanceMinutes(20);
        System.out.println("    swept: " + booking.sweepExpired()
                + "   active holds after: " + booking.activeHolds());
        System.out.println("    The sweeper reclaims memory. Correctness was already handled");
        System.out.println("    lazily at read time, which is why a dead sweeper cannot");
        System.out.println("    oversell anything.");
        System.out.println();
    }

    private static void volatileIsNotAtomic() throws Exception {
        System.out.println("== Why volatile does not make count++ safe ==");

        VolatileCounter volatileCounter = new VolatileCounter();
        AtomicInteger atomic = new AtomicInteger();
        int perThread = 10_000;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            pool.execute(() -> {
                for (int j = 0; j < perThread; j++) {
                    volatileCounter.increment();
                    atomic.incrementAndGet();
                }
                done.countDown();
            });
        }
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        int expected = 8 * perThread;
        System.out.println("  expected:        " + expected);
        System.out.println("  volatile int:    " + volatileCounter.value()
                + (volatileCounter.value() == expected ? "  (got lucky this run)" : "  <- lost updates"));
        System.out.println("  AtomicInteger:   " + atomic.get());
        System.out.println("  volatile gives visibility, not atomicity. count++ is three steps");
        System.out.println("  — read, add, write — and two threads interleave inside it.");
    }

    // ------------------------------------------------------------------

    private interface Attempt {
        boolean run(String userId);
    }

    /** Fires THREADS attempts at the same instant and counts how many won. */
    private static int raceFor(Attempt attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(THREADS);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            String user = "user-" + i;
            pool.execute(() -> {
                try {
                    startGun.await();
                    if (attempt.run(user)) {
                        winners.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();            // release them all together
        finished.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        return winners.get();
    }

    private static int groupRace(boolean sortFirst) throws Exception {
        LockBooking booking = new LockBooking();
        List<String> forward = List.of("A1", "A2");
        List<String> backward = List.of("A2", "A1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        AtomicInteger succeeded = new AtomicInteger();

        List<List<String>> orders = new ArrayList<>(List.of(forward, backward));
        for (int i = 0; i < 2; i++) {
            List<String> seats = orders.get(i);
            String user = "group-" + i;
            pool.execute(() -> {
                try {
                    startGun.await();
                    if (booking.bookGroup(seats, user, sortFirst)) {
                        succeeded.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();
        finished.await(10, TimeUnit.SECONDS);
        pool.shutdown();
        return succeeded.get();
    }
}
