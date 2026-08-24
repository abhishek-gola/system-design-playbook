/**
 * A clock you move by hand.
 *
 * Every TTL demonstration in this folder needs time to pass, and the worst way
 * to make time pass in a teaching example is Thread.sleep - it makes the demo
 * slow, and it makes the output depend on how busy the machine is. Injecting a
 * clock is also what you would do in the real service, because "wait ten minutes
 * for the hold to expire" is not a test anybody wants to run in CI.
 */
public final class ManualClock {

    private long millis;

    public ManualClock() {
        this.millis = 0L;
    }

    public long now() {
        return millis;
    }

    public void advance(long deltaMillis) {
        millis += deltaMillis;
    }

    /** Prints as t+90s, which reads better in a transcript than an epoch number. */
    public String stamp() {
        return "t+" + (millis / 1000) + "s";
    }
}
