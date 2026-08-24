import java.time.Instant;
import java.util.Optional;

/**
 * A gate holds no state about who is parked. It asks the lot for a spot and
 * hands back a ticket.
 *
 * That is what lets you run six entry gates in parallel without them
 * coordinating: all the shared state lives in one place, and the gate is a thin
 * shell over it. If Spot had held the Vehicle, each gate would need to walk the
 * floors itself and you would have six writers to the same structure with no
 * single owner.
 */
public class EntryGate {
    private final String id;
    private final ParkingLot lot;

    public EntryGate(String id, ParkingLot lot) {
        this.id = id;
        this.lot = lot;
    }

    public Optional<Ticket> admit(Vehicle vehicle, Instant at) {
        Optional<Ticket> ticket = lot.park(vehicle, at);
        if (ticket.isPresent()) {
            System.out.println("  [" + id + "] issued " + ticket.get());
        } else {
            System.out.println("  [" + id + "] turned away " + vehicle + " — no spot fits");
        }
        return ticket;
    }
}
