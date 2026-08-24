/**
 * Vehicle *type* is a property, not a subtype.
 *
 * The tempting alternative is Motorbike/Car/Truck classes extending Vehicle.
 * Resist it. Nothing about a truck behaves differently — it just needs a bigger
 * spot and costs more per hour. Both of those are data. Adding a bus here is
 * one new constant and one new row in PricingRule; with a class hierarchy it
 * would be a new class, a matching Spot subclass, and an edit to every switch
 * that ever mentioned a vehicle.
 */
public enum VehicleType {
    MOTORBIKE(SpotSize.SMALL),
    CAR(SpotSize.MEDIUM),
    TRUCK(SpotSize.LARGE);

    private final SpotSize smallestFit;

    VehicleType(SpotSize smallestFit) {
        this.smallestFit = smallestFit;
    }

    public SpotSize smallestFit() {
        return smallestFit;
    }
}
