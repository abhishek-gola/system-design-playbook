/**
 * Composition: a Spot has no meaning without its Floor, and dies with it.
 *
 * Note what is deliberately NOT here: a reference to the parked Vehicle.
 *
 * If Spot held the vehicle, the spot would be the source of truth for "who is
 * parked here", and every gate would have to reach into floors and spots to
 * answer any question. Because the Ticket holds both the vehicle and the spot
 * instead, several entry gates can issue tickets at once without talking to
 * each other, and the exit gate needs nothing but a ticket id.
 *
 * The spot therefore only tracks one bit: is it free?
 */
public class Spot {
    private final String id;
    private final SpotSize size;
    private boolean occupied;

    public Spot(String id, SpotSize size) {
        this.id = id;
        this.size = size;
    }

    public String id() { return id; }
    public SpotSize size() { return size; }
    public boolean isFree() { return !occupied; }

    public boolean fits(VehicleType type) {
        return size.canHold(type.smallestFit());
    }

    /** Single-threaded here on purpose — see lld/10-concurrency for the real version. */
    void occupy() {
        if (occupied) throw new IllegalStateException("spot " + id + " already taken");
        occupied = true;
    }

    void release() {
        occupied = false;
    }

    @Override
    public String toString() {
        return id + "(" + size + ")";
    }
}
