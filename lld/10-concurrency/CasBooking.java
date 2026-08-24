import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cheapest correct answer, and the one to reach for first.
 *
 * putIfAbsent is a single atomic operation: it checks and writes with no window
 * in between. Exactly one caller gets null back, and null means you won.
 *
 * No lock is taken, so nothing blocks, and losers find out immediately rather
 * than waiting their turn to be told no.
 *
 * Note that ConcurrentHashMap is not magic here. Writing
 * `if (!map.containsKey(k)) map.put(k, v)` on a ConcurrentHashMap has exactly
 * the same bug as NaiveBooking — the individual operations are thread-safe, the
 * combination is not. The atomicity has to come from one call.
 */
public class CasBooking {

    private final Map<String, String> owner = new ConcurrentHashMap<>();

    public boolean book(String seatId, String userId) {
        return owner.putIfAbsent(seatId, userId) == null;
    }

    public String ownerOf(String seatId) {
        return owner.get(seatId);
    }
}
