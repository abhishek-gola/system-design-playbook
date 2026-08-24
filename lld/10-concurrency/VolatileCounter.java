/**
 * A counter that is visible but not atomic.
 *
 * volatile guarantees that a write by one thread is seen by the next reader.
 * It guarantees nothing about the read-add-write sequence in between, and
 * count++ is exactly that sequence. Two threads land on the same value, both
 * add one, and one increment vanishes.
 */
public class VolatileCounter {

    private volatile int count;

    public void increment() {
        count++;
    }

    public int value() {
        return count;
    }
}
