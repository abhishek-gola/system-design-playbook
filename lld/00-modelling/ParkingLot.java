import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns the floors (composition) and the open tickets (composition — a ticket is
 * meaningless without the lot that issued it).
 *
 * Everything mutable lives here, in one object, which is what makes the gates
 * disposable. It is also single-threaded, which is a lie the interviewer will
 * call out within ten minutes. The honest answer is in lld/10-concurrency; the
 * short version is that findFree-then-occupy is a check-then-act race and needs
 * to be one atomic step.
 */
public class ParkingLot {
    private final String name;
    private final List<Floor> floors = new ArrayList<>();
    private final Map<String, Ticket> openTickets = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong(1000);

    public ParkingLot(String name) {
        this.name = name;
    }

    public ParkingLot addFloor(Floor floor) {
        floors.add(floor);
        return this;
    }

    /**
     * Optional rather than an exception because "the lot is full" is an
     * expected outcome, not a bug. If the interviewer prefers an exception,
     * agree and move on — it is not worth two minutes.
     */
    public Optional<Ticket> park(Vehicle vehicle, Instant at) {
        for (Floor floor : floors) {
            Optional<Spot> free = floor.findFree(vehicle.type());
            if (free.isPresent()) {
                Spot spot = free.get();
                spot.occupy();
                Ticket ticket = new Ticket(
                        "T" + sequence.incrementAndGet(), vehicle, spot, floor.number(), at);
                openTickets.put(ticket.id(), ticket);
                return Optional.of(ticket);
            }
        }
        return Optional.empty();
    }

    public Ticket release(String ticketId, Instant at) {
        Ticket ticket = openTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("unknown or already-settled ticket " + ticketId);
        }
        ticket.spot().release();
        return ticket;
    }

    public void printOccupancy() {
        long free = 0, total = 0;
        for (Floor f : floors) {
            free += f.freeCount();
            total += f.totalCount();
        }
        System.out.println("  " + name + ": " + (total - free) + "/" + total
                + " occupied, " + openTickets.size() + " open tickets");
    }
}
