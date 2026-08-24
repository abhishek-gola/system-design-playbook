/**
 * A clock you move by hand. Nothing here ever reads real time.
 */
public class ManualTicker implements Ticker {

    private long nowMillis;

    public ManualTicker(long startMillis) {
        this.nowMillis = startMillis;
    }

    @Override
    public long nowMillis() {
        return nowMillis;
    }

    public void advance(long millis) {
        nowMillis += millis;
    }
}
