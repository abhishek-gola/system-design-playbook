import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What actually ships.
 *
 * Reserve the seat with a TTL, take payment, then confirm. The point worth
 * stating explicitly in the interview: this converts a lock held for minutes
 * across a payment call — which you must never do — into a row with an expiry.
 * Nothing is held while you wait on a third party.
 *
 * Expiry here is LAZY: a hold past its TTL is treated as absent the moment
 * anyone asks. That is deliberate. A background sweeper is the answer most
 * candidates give, and it has a window where an expired hold still looks live,
 * plus a dependency on the sweeper being alive. Lazy expiry has neither. Keep
 * the sweeper anyway, but as a tidy-up for memory rather than for correctness —
 * which is exactly what sweepExpired() below is for.
 */
public class HoldThenConfirm {

    public static final long HOLD_MILLIS = 10 * 60_000L;    // ten minutes

    private static final class Hold {
        final String userId;
        final String token;
        final long expiresAt;

        Hold(String userId, String token, long expiresAt) {
            this.userId = userId;
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    private final Ticker ticker;
    private final Map<String, Hold> holds = new ConcurrentHashMap<>();
    private final Map<String, String> confirmed = new ConcurrentHashMap<>();
    private final AtomicLong tokenSequence = new AtomicLong();

    public HoldThenConfirm(Ticker ticker) {
        this.ticker = ticker;
    }

    /** Returns a hold token, or null if somebody else has the seat. */
    public String hold(String seatId, String userId) {
        if (confirmed.containsKey(seatId)) {
            return null;
        }
        String token = "hold-" + tokenSequence.incrementAndGet();
        Hold candidate = new Hold(userId, token, ticker.millis() + HOLD_MILLIS);

        // One atomic operation that both checks and writes. The lambda only
        // runs while the bin is locked, so two callers cannot both decide the
        // existing hold has expired and both replace it.
        Hold winner = holds.compute(seatId, (id, existing) -> {
            if (existing == null || existing.expiresAt <= ticker.millis()) {
                return candidate;               // free, or the old hold has lapsed
            }
            return existing;                    // somebody still has it
        });

        return winner == candidate ? token : null;
    }

    /**
     * The token is a fencing check. Without it, a user whose hold expired and
     * was taken by someone else could still come back from a slow payment page
     * and confirm a seat that is no longer theirs.
     */
    public boolean confirm(String seatId, String token) {
        Hold hold = holds.get(seatId);
        if (hold == null || !hold.token.equals(token)) {
            return false;
        }
        if (hold.expiresAt <= ticker.millis()) {
            return false;                       // paid too late
        }
        confirmed.put(seatId, hold.userId);
        holds.remove(seatId);
        return true;
    }

    public boolean release(String seatId, String token) {
        Hold hold = holds.get(seatId);
        if (hold != null && hold.token.equals(token)) {
            holds.remove(seatId);
            return true;
        }
        return false;
    }

    /** Housekeeping, not correctness. Correctness is handled lazily in hold(). */
    public List<String> sweepExpired() {
        List<String> swept = new ArrayList<>();
        long now = ticker.millis();
        for (Map.Entry<String, Hold> entry : holds.entrySet()) {
            if (entry.getValue().expiresAt <= now) {
                holds.remove(entry.getKey(), entry.getValue());
                swept.add(entry.getKey());
            }
        }
        return swept;
    }

    public String confirmedOwner(String seatId) { return confirmed.get(seatId); }
    public int activeHolds()                    { return holds.size(); }
}
