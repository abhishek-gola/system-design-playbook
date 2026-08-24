/**
 * The one you should reach for by default.
 *
 * Correct, obviously correct, and thread-safe because the JVM guarantees static
 * initialisation happens once under a lock it manages for you. The only thing
 * you give up is laziness, and if the constructor is cheap that costs nothing.
 *
 * "I'd use the eager version unless construction is expensive" is a better
 * interview answer than double-checked locking, and most candidates skip
 * straight past it looking for something clever.
 */
public class EagerConfig {

    private static final EagerConfig INSTANCE = new EagerConfig();

    private final long constructedAtNanos;

    private EagerConfig() {
        constructedAtNanos = System.nanoTime();
        System.out.println("      EagerConfig constructed (at class-load time)");
    }

    public static EagerConfig get() {
        return INSTANCE;
    }

    public long constructedAtNanos() { return constructedAtNanos; }
}
