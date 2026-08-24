/**
 * A clock the test drives by hand.
 *
 * The field is volatile because the worker threads in the stampede demo read it
 * while the main thread sets it. Only the main thread ever writes, and it only
 * writes while the workers are parked, so a plain volatile write is enough — no
 * compare-and-set, no lock.
 */
public class ManualTicker implements Ticker {

    private volatile long now;

    public ManualTicker(long startMillis) {
        this.now = startMillis;
    }

    @Override
    public long millis() {
        return now;
    }

    /** Jump to an absolute instant. Main thread only. */
    public void advanceTo(long millis) {
        this.now = millis;
    }
}
