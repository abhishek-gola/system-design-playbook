import java.time.Duration;
import java.time.Instant;

/**
 * PricingRule arrives as a method parameter, not a field. That is the
 * dependency relationship, and it is the honest one here: the gate needs a
 * pricing rule for the length of one call and has no opinion about it after
 * that. Ops can swap the rule between two cars without touching the gate.
 */
public class ExitGate {
    private final String id;
    private final ParkingLot lot;

    public ExitGate(String id, ParkingLot lot) {
        this.id = id;
        this.lot = lot;
    }

    public long settle(String ticketId, Instant at, PricingRule pricing) {
        Ticket ticket = lot.release(ticketId, at);
        Duration parked = Duration.between(ticket.entryAt(), at);
        long fee = pricing.feeFor(ticket.vehicle().type(), parked);
        System.out.println("  [" + id + "] " + ticket.vehicle()
                + " parked " + parked.toMinutes() + " min, fee Rs " + fee);
        return fee;
    }
}
