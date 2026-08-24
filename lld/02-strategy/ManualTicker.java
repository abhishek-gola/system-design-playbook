/** A clock you drive by hand, so the demo prints the same thing every run. */
public class ManualTicker implements Ticker {
    private long now;

    public ManualTicker(long start) {
        this.now = start;
    }

    @Override
    public long millis() {
        return now;
    }

    public void advance(long millis) {
        now += millis;
    }
}
