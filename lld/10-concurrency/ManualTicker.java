/** A clock you drive by hand, so a ten-minute TTL takes no time to demonstrate. */
public class ManualTicker implements Ticker {
    private long now;

    public ManualTicker(long start) { this.now = start; }

    @Override
    public long millis() { return now; }

    public void advanceMinutes(long minutes) { now += minutes * 60_000; }
}
