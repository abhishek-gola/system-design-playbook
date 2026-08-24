/**
 * Minor units as a long, currency explicit. Never a double, never a bare int
 * whose unit you have to guess from the variable name.
 *
 * One provider takes paise and another takes rupees with two decimal places.
 * Getting that translation wrong is a hundred-fold billing error rather than a
 * crash, which is exactly the kind of thing an adapter exists to contain.
 */
public final class Money {
    private final long minorUnits;
    private final String currency;

    private Money(long minorUnits, String currency) {
        this.minorUnits = minorUnits;
        this.currency = currency;
    }

    public static Money rupees(long rupees)      { return new Money(rupees * 100, "INR"); }
    public static Money paise(long paise)        { return new Money(paise, "INR"); }

    public long minorUnits()  { return minorUnits; }
    public String currency()  { return currency; }

    /** For the provider that insists on a decimal string. */
    public String asDecimalString() {
        return (minorUnits / 100) + "." + String.format("%02d", Math.abs(minorUnits % 100));
    }

    @Override
    public String toString() { return currency + " " + asDecimalString(); }
}
