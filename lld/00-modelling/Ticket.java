import java.time.Instant;

/**
 * The centre of the design.
 *
 * Association in both directions: the ticket points at a Vehicle and at a Spot,
 * and owns neither. Deleting a ticket must not delete the car or demolish the
 * bay. That is the whole reason this class exists rather than storing the
 * vehicle on the spot.
 */
public class Ticket {
    private final String id;
    private final Vehicle vehicle;
    private final Spot spot;
    private final int floorNumber;
    private final Instant entryAt;

    public Ticket(String id, Vehicle vehicle, Spot spot, int floorNumber, Instant entryAt) {
        this.id = id;
        this.vehicle = vehicle;
        this.spot = spot;
        this.floorNumber = floorNumber;
        this.entryAt = entryAt;
    }

    public String id() { return id; }
    public Vehicle vehicle() { return vehicle; }
    public Spot spot() { return spot; }
    public int floorNumber() { return floorNumber; }
    public Instant entryAt() { return entryAt; }

    @Override
    public String toString() {
        return id + " " + vehicle + " -> floor " + floorNumber + " spot " + spot;
    }
}
