import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composition both ways: the Floor owns its Spots, and the ParkingLot owns its
 * Floors. Delete the lot and every floor and spot goes with it, which is the
 * right answer to "if I delete the container, should this go too?".
 */
public class Floor {
    private final int number;
    private final List<Spot> spots = new ArrayList<>();

    public Floor(int number, int small, int medium, int large) {
        this.number = number;
        add(small,  SpotSize.SMALL);
        add(medium, SpotSize.MEDIUM);
        add(large,  SpotSize.LARGE);
    }

    private void add(int count, SpotSize size) {
        for (int i = 1; i <= count; i++) {
            spots.add(new Spot("F" + number + "-" + size.name().charAt(0) + i, size));
        }
    }

    public int number() { return number; }

    /**
     * Smallest spot that fits, so a motorbike doesn't eat the last truck bay.
     * A linear scan is honest at interview scale; say out loud that you'd keep
     * a free-list per size if the lot had ten thousand spots.
     */
    public Optional<Spot> findFree(VehicleType type) {
        return spots.stream()
                .filter(Spot::isFree)
                .filter(s -> s.fits(type))
                .min((a, b) -> a.size().compareTo(b.size()));
    }

    public long freeCount() {
        return spots.stream().filter(Spot::isFree).count();
    }

    public int totalCount() {
        return spots.size();
    }
}
