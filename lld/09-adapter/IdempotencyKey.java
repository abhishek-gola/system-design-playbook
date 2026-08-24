/**
 * Every provider supports one, and every provider calls it something different.
 * Translating that name is part of the adapter's job.
 */
public final class IdempotencyKey {
    private final String value;

    public IdempotencyKey(String value) { this.value = value; }

    public String value() { return value; }

    @Override
    public String toString() { return value; }
}
