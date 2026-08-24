import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;

/**
 * Contention, one rung of the ladder at a time.
 *
 * The seat-booking problem is the same one solved in lld/10-concurrency inside a
 * single process. This is the system-scale version: the same overselling bug,
 * but the contending parties are separate machines, so a synchronized block is
 * not available and the coordination has to live somewhere both of them can see.
 */
public final class Demo {

    private static final int ROUNDS = 200;
    private static final int BUYERS_PER_ROUND = 8;

    public static void main(String[] args) throws Exception {
        theRace();
        compareAndSet();
        optimistic();
        pessimistic();
        holdThenConfirm();
    }

    // ---------------------------------------------------------------- rung 0

    private static void theRace() throws Exception {
        section("0. No coordination at all");
        System.out.println("  " + BUYERS_PER_ROUND + " buyers go for seat 14A at once, " + ROUNDS + " times over.");
        System.out.println("  Each one reads the seat, sees it free, and claims it.");
        System.out.println();

        int oversold = SeatRace.naiveOversoldRounds(ROUNDS, BUYERS_PER_ROUND);
        System.out.println("  rounds where more than one buyer believed they had won: " + oversold + " / " + ROUNDS);
        if (oversold == 0) {
            System.out.println("  The race did not reproduce on this run. It is still there - a race that");
            System.out.println("  does not fire is not a race that is fixed, and this is exactly why you");
            System.out.println("  cannot test your way to confidence here.");
        } else {
            System.out.println("  Every one of those rounds is a seat sold twice and a refund conversation.");
        }
    }

    // ---------------------------------------------------------------- rung 1

    private static void compareAndSet() throws Exception {
        section("1. Compare-and-set: fuse the check and the act");
        System.out.println("  Identical experiment, one line different: putIfAbsent instead of get-then-put.");
        System.out.println();

        int oversold = SeatRace.casOversoldRounds(ROUNDS, BUYERS_PER_ROUND);
        System.out.println("  rounds where more than one buyer believed they had won: " + oversold + " / " + ROUNDS);
        System.out.println();
        System.out.println("  In a database this is INSERT ... ON CONFLICT DO NOTHING, or an UPDATE whose");
        System.out.println("  WHERE clause names the value you expect to find. It is the cheapest rung that");
        System.out.println("  is actually correct, and when all the contended state lives in one row it is");
        System.out.println("  the whole answer. Reach for anything heavier only once that stops being true.");
    }

    // ---------------------------------------------------------------- rung 2

    private static void optimistic() {
        section("2. Optimistic concurrency: a version column and the affected-row count");
        OptimisticSeats seats = new OptimisticSeats("14A", "14B", "14C");
        OptimisticSeats.Row snapshot = seats.read("14A");
        System.out.println("  Three buyers all SELECT seat 14A and all see version " + snapshot.version() + ".");
        System.out.println();

        List<String> lost = new ArrayList<>();
        for (String buyer : new String[]{"ana", "ben", "cara"}) {
            int affected = seats.claim("14A", buyer, snapshot.version());
            System.out.println("  " + buyer + ": UPDATE seats SET owner='" + buyer + "', version=version+1"
                    + " WHERE id='14A' AND version=" + snapshot.version() + "  ->  " + affected + " row(s)");
            if (affected == 0) {
                lost.add(buyer);
            }
        }

        System.out.println();
        System.out.println("  Zero rows affected is not an error the driver will raise for you. Nobody throws.");
        System.out.println("  The losers have to notice the count themselves and redo the work:");
        String[] fallback = {"14B", "14C"};
        int i = 0;
        for (String buyer : lost) {
            String seat = fallback[i++];
            OptimisticSeats.Row row = seats.read(seat);
            int affected = seats.claim(seat, buyer, row.version());
            System.out.println("    " + buyer + " re-reads, picks " + seat + " at version " + row.version()
                    + "  ->  " + affected + " row(s)");
        }

        System.out.println();
        System.out.println("  wasted attempts: " + seats.lostAttempts() + ". That number is the price of the pattern,");
        System.out.println("  and it climbs with contention. Optimistic is right when conflicts are rare -");
        System.out.println("  editing your own profile, adjusting a warehouse count. It is the wrong choice");
        System.out.println("  for the last seat at a Taylor Swift show, where every writer conflicts and you");
        System.out.println("  have built a retry storm.");
    }

    // ---------------------------------------------------------------- rung 3

    private static void pessimistic() throws Exception {
        section("3. Pessimistic locking, and the deadlock waiting inside it");
        System.out.println("  Two group bookings want the same pair of seats, and each asks for them in");
        System.out.println("  the order the customer typed. No sorting.");
        System.out.println();

        PessimisticSeats broken = new PessimisticSeats();
        CyclicBarrier gate = new CyclicBarrier(2);
        String[] results = new String[2];
        Thread x = bookingThread(broken, "group-x", "14A", "14B", false, gate, results, 0);
        Thread y = bookingThread(broken, "group-y", "14B", "14A", false, gate, results, 1);
        x.start();
        y.start();
        x.join();
        y.join();

        int deadlocked = 0;
        for (String line : results) {
            System.out.println("  " + line);
            if (line != null && line.contains("deadlock")) {
                deadlocked++;
            }
        }
        System.out.println();
        System.out.println("  threads that could not make progress: " + deadlocked);
        System.out.println("  Each holds what the other needs. Only the timeout on tryLock turned this into");
        System.out.println("  a printed line instead of a hung request thread. Without a deadline they would");
        System.out.println("  both still be sitting there.");

        System.out.println();
        System.out.println("  Same two bookings, seats locked in sorted order:");
        PessimisticSeats fixed = new PessimisticSeats();
        String[] fixedResults = new String[2];
        Thread a = bookingThread(fixed, "group-x", "14A", "14B", true, null, fixedResults, 0);
        Thread b = bookingThread(fixed, "group-y", "14B", "14A", true, null, fixedResults, 1);
        a.start();
        b.start();
        a.join();
        b.join();
        for (String line : fixedResults) {
            System.out.println("    " + line);
        }
        System.out.println("    14A -> " + fixed.ownerOf("14A") + ", 14B -> " + fixed.ownerOf("14B"));
        System.out.println();
        System.out.println("  A cycle needs two threads acquiring in opposite orders. Impose one global order");
        System.out.println("  on the keys and the cycle cannot form. Your database does exactly this to itself");
        System.out.println("  with row locks, which is why the usual cause of a production deadlock is two code");
        System.out.println("  paths touching the same two tables the other way round.");
    }

    private static Thread bookingThread(PessimisticSeats seats, String buyer, String first, String second,
                                        boolean ordered, CyclicBarrier gate, String[] out, int slot) {
        return new Thread(() -> {
            try {
                out[slot] = seats.bookPair(buyer, first, second, ordered, gate, 150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out[slot] = buyer + ": interrupted";
            }
        });
    }

    // -------------------------------------------------------- what ships

    private static void holdThenConfirm() {
        section("4. Hold, then confirm - the design that actually ships");
        ManualClock clock = new ManualClock();
        long ttl = 600_000L; // ten minutes, which is a product decision, not a technical one
        HoldBook book = new HoldBook(clock, ttl, "14A", "14B");

        System.out.println("  " + clock.stamp() + "  ana holds 14A");
        Optional<HoldBook.Hold> anaHold = book.tryHold("14A", "ana");
        System.out.println("            " + book.describe("14A"));

        System.out.println("  " + clock.stamp() + "  ben tries the same seat");
        Optional<HoldBook.Hold> benHold = book.tryHold("14A", "ben");
        System.out.println("            ben got a hold: " + benHold.isPresent() + " - the seat is off the market");

        clock.advance(90_000L);
        System.out.println("  " + clock.stamp() + "  ana finishes paying and confirms");
        System.out.println("            " + book.confirm(anaHold.orElseThrow()));
        System.out.println("            nothing was locked for those 90 seconds. The payment provider being");
        System.out.println("            slow costs one customer their checkout, not the whole venue.");

        System.out.println();
        System.out.println("  Now the abandoned hold, and the stale client behind it.");
        System.out.println("  " + clock.stamp() + "  cara holds 14B, then her process stalls");
        HoldBook.Hold caraHold = book.tryHold("14B", "cara").orElseThrow();
        System.out.println("            " + book.describe("14B"));

        clock.advance(ttl + 1_000L);
        System.out.println("  " + clock.stamp() + "  the sweeper runs");
        System.out.println("            released: " + book.sweepExpiredHolds());

        System.out.println("  " + clock.stamp() + "  dan holds the freed seat");
        HoldBook.Hold danHold = book.tryHold("14B", "dan").orElseThrow();
        System.out.println("            " + book.describe("14B"));

        System.out.println("  " + clock.stamp() + "  cara's process wakes up and confirms, still believing it holds 14B");
        System.out.println("            without fencing this would have been accepted: " + book.naiveConfirmWouldSucceed(caraHold));
        System.out.println("            " + book.confirm(caraHold));

        System.out.println("  " + clock.stamp() + "  dan confirms");
        System.out.println("            " + book.confirm(danHold));

        System.out.println();
        System.out.println("  final ledger");
        System.out.println("    " + book.describe("14A"));
        System.out.println("    " + book.describe("14B"));
        System.out.println("    seats sold: 2, seats sold twice: 0");
        System.out.println();
        System.out.println("  The remaining hole is the one they will ask about: the payment succeeds and the");
        System.out.println("  confirm fails. An idempotency key on the payment stops the retry charging twice,");
        System.out.println("  and a reconciliation job finds the paid-but-unconfirmed holds and repairs them.");
        System.out.println("  Both of those are built in hld/06-multi-step-processes.");
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " " + "=".repeat(Math.max(0, 72 - title.length())));
        System.out.println();
    }
}
