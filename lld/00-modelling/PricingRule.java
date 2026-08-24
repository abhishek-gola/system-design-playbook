import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Deliberately a plain class, not an interface, and deliberately not a field on
 * anything. The exit gate takes one as a method parameter — that is what makes
 * the relationship a dependency (dashed line) rather than an association.
 *
 * This is the first place a pattern genuinely wants to appear. The moment the
 * requirement becomes "weekends cost more" or "the first fifteen minutes are
 * free for members", turn it into an interface and you have Strategy.
 * See lld/02-strategy.
 */
public class PricingRule {
    private final Map<VehicleType, Long> rupeesPerHour = new EnumMap<>(VehicleType.class);

    public PricingRule() {
        rupeesPerHour.put(VehicleType.MOTORBIKE, 20L);
        rupeesPerHour.put(VehicleType.CAR,       40L);
        rupeesPerHour.put(VehicleType.TRUCK,     80L);
    }

    /** Part hours round up, and there is always a minimum of one hour. */
    public long feeFor(VehicleType type, Duration parked) {
        long hours = Math.max(1, (parked.toMinutes() + 59) / 60);
        return hours * rupeesPerHour.get(type);
    }
}
