/**
 * Time as a dependency rather than as a global.
 *
 * Every cache in this folder needs to know whether an entry has expired, and if
 * that question is answered by System.currentTimeMillis() inside the cache then
 * the only way to test expiry is to sleep. Injecting the clock means the demo
 * can jump a cache forward by an hour and print the same numbers every run.
 *
 * The interviewer's version of this question is "how would you test the TTL
 * behaviour?" — and this interface is the whole answer.
 */
public interface Ticker {
    long millis();
}
