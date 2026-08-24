/**
 * Aggregation, from the lot's point of view: the vehicle exists before it parks
 * and after it leaves. Deleting the parking lot must not delete the car.
 *
 * That is exactly why the lot never holds a collection of vehicles. It holds
 * tickets, and tickets point at vehicles.
 */
public class Vehicle {
    private final String plate;
    private final VehicleType type;

    public Vehicle(String plate, VehicleType type) {
        this.plate = plate;
        this.type = type;
    }

    public String plate() { return plate; }
    public VehicleType type() { return type; }

    @Override
    public String toString() {
        return type + " " + plate;
    }
}
