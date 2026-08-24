/**
 * Time, as a dependency rather than as a global.
 *
 * A visibility timeout is a promise about elapsed time, and so is exponential
 * backoff. Demonstrating either against the real clock means sleeping for real
 * seconds, and a demo nobody waits for is a demo nobody runs. With an injected
 * clock the whole simulation — leases expiring, backoff windows passing, a
 * poison message exhausting its retries — runs in a few milliseconds and prints
 * the same thing on every machine.
 *
 * This is also the honest answer when someone asks how you would test retry
 * behaviour. You do not sleep. You inject the clock.
 */
public interface Ticker {
    long nowMillis();
}
