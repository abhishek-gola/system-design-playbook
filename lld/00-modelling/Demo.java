import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A parking lot with no design patterns in it at all. That is the point of this
 * folder: the relationships carry the design, and patterns are what you add on
 * the second pass, once you can name which change each one absorbs.
 */
public class Demo {

    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot("Indiranagar")
                .addFloor(new Floor(1, 1, 1, 1))   // 1 small, 1 medium, 1 large
                .addFloor(new Floor(2, 2, 0, 0));  // bikes only

        EntryGate north = new EntryGate("entry-north", lot);
        EntryGate south = new EntryGate("entry-south", lot);
        ExitGate  exit  = new ExitGate("exit-main", lot);
        PricingRule pricing = new PricingRule();

        // Fixed timestamp so the fees below are the same on every run.
        Instant t0 = Instant.parse("2026-08-24T09:00:00Z");

        System.out.println("Two gates admitting at the same time:");
        Optional<Ticket> bike = north.admit(new Vehicle("KA-01-AB-1234", VehicleType.MOTORBIKE), t0);
        Optional<Ticket> car  = south.admit(new Vehicle("KA-05-XY-9876", VehicleType.CAR), t0);
        lot.printOccupancy();

        System.out.println();
        System.out.println("A second car. The medium bay is gone, so it takes the truck bay,");
        System.out.println("because a bigger spot can hold a smaller vehicle:");
        Optional<Ticket> car2 = south.admit(new Vehicle("KA-03-CD-1111", VehicleType.CAR), t0);
        lot.printOccupancy();

        System.out.println();
        System.out.println("And now a truck arrives:");
        north.admit(new Vehicle("KA-09-TR-4444", VehicleType.TRUCK), t0);
        System.out.println("  A car is sitting in the only bay a truck can use. Smallest-fit is");
        System.out.println("  greedy, and greedy allocation starves the large vehicles.");
        System.out.println("  Worth saying out loud in an interview: the fix is reserving bays");
        System.out.println("  by type, or an allocation policy that looks ahead. That is the");
        System.out.println("  second place a Strategy would earn its keep.");

        System.out.println();
        System.out.println("Settling up:");
        exit.settle(bike.get().id(), t0.plus(Duration.ofMinutes(30)),  pricing);  // rounds to 1 hour
        exit.settle(car.get().id(),  t0.plus(Duration.ofMinutes(150)), pricing);  // rounds to 3 hours
        lot.printOccupancy();

        System.out.println();
        System.out.println("The same ticket twice is rejected:");
        try {
            exit.settle(bike.get().id(), t0.plus(Duration.ofMinutes(40)), pricing);
        } catch (IllegalArgumentException e) {
            System.out.println("  refused: " + e.getMessage());
        }

        System.out.println();
        System.out.println("Still parked: " + car2.get());
        System.out.println();
        System.out.println("Second pass — where a pattern would now earn its keep:");
        System.out.println("  PricingRule  -> Strategy, the moment weekend or member rates appear");
        System.out.println("  Spot status  -> a two-state machine, and it stays that small");
        System.out.println("  findFree     -> a Strategy, and the demo above shows why:");
        System.out.println("                  smallest-fit is one policy among several");
        System.out.println("  Nothing else. Adding patterns anywhere else here is decoration.");
    }
}
