/**
 * Time, as a dependency rather than as a global.
 *
 * The whole point of this folder is a registry entry that expires and a crashed
 * server that cleans itself up because of it. Demonstrating that against
 * System.currentTimeMillis() would mean sleeping for real seconds, and a demo
 * nobody waits for is a demo nobody runs. Injecting the clock lets the Demo
 * jump forward by a minute in one statement and keeps the output identical on
 * every machine.
 *
 * The same trick is worth mentioning in an interview when someone asks how you
 * would test TTL or timeout behaviour. "I would not sleep, I would inject the
 * clock" is a short answer that lands well.
 */
public interface Ticker {
    long nowMillis();
}
