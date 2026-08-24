import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pessimistic locking, one lock per seat rather than one for the whole show.
 *
 * Granularity is the interesting decision here. `synchronized (this)` around
 * the booking method is also correct, and turns a 500-seat cinema into a queue
 * of one. A lock per seat lets 500 bookings proceed at once, and costs you a map
 * of locks plus the deadlock risk that shows up the moment one booking needs two
 * seats.
 *
 * The rule: lock the smallest thing that makes the invariant true.
 */
public class LockBooking {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Map<String, String> owner = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(String seatId) {
        return locks.computeIfAbsent(seatId, id -> new ReentrantLock());
    }

    public boolean book(String seatId, String userId) {
        ReentrantLock lock = lockFor(seatId);
        lock.lock();
        try {
            if (owner.containsKey(seatId)) {
                return false;
            }
            owner.put(seatId, userId);
            return true;
        } finally {
            lock.unlock();          // always in a finally, always
        }
    }

    /**
     * A group booking: all seats or none.
     *
     * `sortFirst` is the entire lesson. Two groups grabbing {A1, A2} and
     * {A2, A1} at the same time will each hold one lock and wait forever for the
     * other — a textbook deadlock. Acquiring in a fixed global order makes it
     * impossible, because there is no cycle to form.
     *
     * tryLock with a timeout is the safety net rather than the fix. It turns a
     * hang into a failure you can see and retry, which is what you want in
     * production, but it does not stop the contention. Fix the ordering.
     */
    public boolean bookGroup(java.util.List<String> seatIds, String userId, boolean sortFirst) {
        java.util.List<String> order = new java.util.ArrayList<>(seatIds);
        if (sortFirst) {
            java.util.Collections.sort(order);
        }

        java.util.List<ReentrantLock> held = new java.util.ArrayList<>();
        try {
            for (String seatId : order) {
                ReentrantLock lock = lockFor(seatId);
                boolean acquired;
                try {
                    acquired = lock.tryLock(300, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                if (!acquired) {
                    return false;               // timed out — treat as a lost race
                }
                held.add(lock);

                // Give the other thread time to grab the lock we want next.
                // Without this the demo would usually finish too fast to collide.
                for (int i = 0; i < 5_000; i++) {
                    Thread.onSpinWait();
                }
            }

            for (String seatId : order) {
                if (owner.containsKey(seatId)) {
                    return false;
                }
            }
            for (String seatId : order) {
                owner.put(seatId, userId);
            }
            return true;

        } finally {
            for (int i = held.size() - 1; i >= 0; i--) {
                held.get(i).unlock();
            }
        }
    }

    public String ownerOf(String seatId) {
        return owner.get(seatId);
    }
}
