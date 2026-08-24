/**
 * Time as a dependency rather than an ambient global.
 *
 * Every rate limiting algorithm is a function of the clock, so a limiter that
 * calls System.currentTimeMillis() internally can only be tested by sleeping.
 * Inject it and the tests run in microseconds and never flake.
 *
 * Mention this unprompted in the interview. It is a small thing that reads as
 * someone who has written tests for this kind of code before.
 */
public interface Ticker {
    long millis();

    Ticker SYSTEM = System::currentTimeMillis;
}
