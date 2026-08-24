import java.util.concurrent.atomic.AtomicInteger;

/**
 * What Effective Java recommends, and the only version that survives both
 * serialisation and reflection without extra work.
 *
 * The two attacks it closes:
 *
 * Serialisation — a hand-rolled singleton read back from a stream is a second
 * instance unless you write readResolve(). An enum constant deserialises to the
 * same constant by definition.
 *
 * Reflection — setAccessible(true) on a private constructor defeats every other
 * version here. The JVM refuses to construct an enum reflectively.
 *
 * The demo shows both. The cost is that an enum cannot extend a class and feels
 * odd to some reviewers, which is the honest trade-off to mention.
 */
public enum EnumConfig {
    INSTANCE;

    private final AtomicInteger reads = new AtomicInteger();

    public String get(String key) {
        reads.incrementAndGet();
        return switch (key) {
            case "region" -> "in-south";
            case "env"    -> "production";
            default       -> null;
        };
    }

    public int reads() { return reads.get(); }
}
