import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pessimistic locking: take the lock, do the work, release the lock.
 *
 * A per-seat ReentrantLock here stands in for SELECT ... FOR UPDATE on a seat
 * row, or a Redis lock keyed on the seat id. It is correct, it is the easiest
 * thing to explain, and it is fine as long as the work inside the lock is
 * short and local.
 *
 * The interesting part is what happens when one booking needs two seats. Two
 * groups asking for the same pair in opposite orders will each take one seat
 * and then wait forever for the other. That is a genuine deadlock, and the fix
 * is not cleverness, it is a total order: sort the keys before you lock them,
 * and the cycle cannot form. Database row locks behave exactly the same way,
 * which is why a deadlock in production so often turns out to be two code paths
 * updating the same two tables in different orders.
 *
 * Every lock here is taken with a timeout. A lock you can wait on forever is a
 * lock that will one day take your whole service down, and tryLock with a
 * deadline is what turns a hang into an error you can see and alert on.
 */
public final class PessimisticSeats {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, String> owners = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(String seatId) {
        return locks.computeIfAbsent(seatId, id -> new ReentrantLock());
    }

    /**
     * Book two seats for one group.
     *
     * @param orderLocks when true, the seats are locked in sorted order rather than
     *                   in the order the customer asked for them. That single line is
     *                   the whole fix.
     * @param gate       optional rendezvous used only by the broken demo, to guarantee
     *                   both threads are holding one lock each before either reaches
     *                   for the second. Pass null and the method just runs.
     */
    public String bookPair(String buyer, String seatA, String seatB,
                           boolean orderLocks, CyclicBarrier gate, long waitMillis)
            throws InterruptedException {

        String first = seatA;
        String second = seatB;
        if (orderLocks && first.compareTo(second) > 0) {
            first = seatB;
            second = seatA;
        }

        ReentrantLock firstLock = lockFor(first);
        ReentrantLock secondLock = lockFor(second);

        if (!firstLock.tryLock(waitMillis, TimeUnit.MILLISECONDS)) {
            return buyer + ": gave up waiting for " + first;
        }
        try {
            if (gate != null) {
                try {
                    gate.await(waitMillis * 4, TimeUnit.MILLISECONDS);
                } catch (BrokenBarrierException | TimeoutException e) {
                    return buyer + ": rendezvous failed, cannot demonstrate the deadlock";
                }
            }

            if (!secondLock.tryLock(waitMillis, TimeUnit.MILLISECONDS)) {
                // Without the timeout this thread would sit here until the process
                // is restarted, holding a lock the other thread is waiting on.
                return buyer + ": holds " + first + ", timed out on " + second + "  <-- deadlock";
            }
            try {
                owners.put(first, buyer);
                owners.put(second, buyer);
                return buyer + ": booked " + first + " and " + second;
            } finally {
                secondLock.unlock();
            }
        } finally {
            // The unlock lives in a finally, always. A lock released only on the
            // happy path is a lock that leaks the first time the work throws.
            firstLock.unlock();
        }
    }

    public String ownerOf(String seatId) {
        return owners.getOrDefault(seatId, "unsold");
    }
}
