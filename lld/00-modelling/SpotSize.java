/**
 * Ordered smallest to largest. A bigger spot can hold a smaller vehicle,
 * which is why this is an enum with an ordering and not three booleans.
 */
public enum SpotSize {
    SMALL, MEDIUM, LARGE;

    public boolean canHold(SpotSize needed) {
        return this.ordinal() >= needed.ordinal();
    }
}
