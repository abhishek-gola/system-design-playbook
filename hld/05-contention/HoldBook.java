import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The design that actually ships: hold, then confirm.
 *
 * Everything above this class is a way of making the claim atomic. None of it
 * solves the real problem, which is that the claim is not the slow part - the
 * payment is. Holding a lock across a call to Stripe is how you turn a five
 * second outage at your payment provider into every seat in the building being
 * unbuyable.
 *
 * So the lock becomes a row. Reserve the seat, write an expiry ten minutes out,
 * let the user pay at human speed, then confirm. The seat is unavailable to
 * everybody else during that window, but nothing is holding a lock and no
 * request is blocked. If the user abandons the checkout, the hold expires and
 * the seat comes back on its own. This is the sentence to say out loud in the
 * interview: a hold with a TTL converts a lock held across a slow external call
 * into a database row with an expiry.
 *
 * The map is a stand-in for a seats table plus a holds table. In production the
 * expiry is either a TTL index the database enforces (Mongo, DynamoDB, a Redis
 * key) or a sweeper job - and you want both, because a sweeper that falls behind
 * should cost you availability of a few seats, not correctness.
 */
public final class HoldBook {

    public enum SeatState { FREE, HELD, SOLD }

    /**
     * The fencing token is the part people leave out.
     *
     * It is a number that only ever goes up, handed out with the hold. Confirm
     * presents it, and the server refuses anything that is not the current one.
     * Without it a client that stalled - a long garbage collection pause, a
     * network partition, a VM that got frozen and thawed - can wake up after its
     * hold expired, after somebody else took the seat, and confirm anyway. It
     * believes it still holds the lock and it is wrong, and no amount of TTL
     * tuning fixes that, because the stall can always be longer than the TTL.
     * This is Kleppmann's argument against naive Redlock in one field.
     */
    public record Hold(String seatId, String buyer, long fencingToken, long expiresAt) {
    }

    private final ManualClock clock;
    private final long ttlMillis;
    private final Map<String, SeatState> state = new LinkedHashMap<>();
    private final Map<String, Hold> holds = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();

    // Monotonic and never reused. A Redis INCR or a database sequence in real life.
    private long nextToken = 0;

    public HoldBook(ManualClock clock, long ttlMillis, String... seatIds) {
        this.clock = clock;
        this.ttlMillis = ttlMillis;
        for (String id : seatIds) {
            state.put(id, SeatState.FREE);
        }
    }

    /** Atomic in the same sense as the CAS above: one conditional write, not a read then a write. */
    public Optional<Hold> tryHold(String seatId, String buyer) {
        if (state.get(seatId) != SeatState.FREE) {
            return Optional.empty();
        }
        nextToken++;
        Hold hold = new Hold(seatId, buyer, nextToken, clock.now() + ttlMillis);
        state.put(seatId, SeatState.HELD);
        holds.put(seatId, hold);
        return Optional.of(hold);
    }

    /**
     * The sweeper. Runs on a timer, releases holds whose deadline has passed.
     *
     * Notice it only touches seats still in HELD. A confirmed sale is never
     * swept, however late the sweeper runs, because the state machine and not
     * the clock decides what is sellable.
     */
    public List<String> sweepExpiredHolds() {
        List<String> released = new ArrayList<>();
        for (Hold hold : new ArrayList<>(holds.values())) {
            if (hold.expiresAt() <= clock.now() && state.get(hold.seatId()) == SeatState.HELD) {
                state.put(hold.seatId(), SeatState.FREE);
                holds.remove(hold.seatId());
                released.add(hold.seatId());
            }
        }
        return released;
    }

    /** Confirm the sale. The token is checked before anything is written. */
    public String confirm(Hold presented) {
        String seatId = presented.seatId();
        if (state.get(seatId) == SeatState.SOLD) {
            return "rejected: " + seatId + " was already sold to " + owners.get(seatId);
        }
        Hold current = holds.get(seatId);
        if (current == null) {
            return "rejected: the hold on " + seatId + " expired and was swept";
        }
        if (current.fencingToken() != presented.fencingToken()) {
            return "rejected: stale fencing token " + presented.fencingToken()
                    + ", the current holder is " + current.buyer() + " with token " + current.fencingToken();
        }
        state.put(seatId, SeatState.SOLD);
        owners.put(seatId, presented.buyer());
        holds.remove(seatId);
        return "confirmed: " + seatId + " sold to " + presented.buyer();
    }

    /**
     * A dry run of the confirm you get without fencing tokens - "is this seat
     * held? then let them through". Nothing is written; this exists so the demo
     * can print what the naive version would have done.
     */
    public boolean naiveConfirmWouldSucceed(Hold presented) {
        return state.get(presented.seatId()) == SeatState.HELD;
    }

    public String describe(String seatId) {
        SeatState s = state.get(seatId);
        if (s == SeatState.SOLD) {
            return seatId + " SOLD to " + owners.get(seatId);
        }
        if (s == SeatState.HELD) {
            Hold h = holds.get(seatId);
            return seatId + " HELD by " + h.buyer() + " (token " + h.fencingToken()
                    + ", expires t+" + (h.expiresAt() / 1000) + "s)";
        }
        return seatId + " FREE";
    }
}
