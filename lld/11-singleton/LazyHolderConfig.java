/**
 * Lazy, thread-safe, and no synchronisation anywhere.
 *
 * Holder is not initialised until get() first touches it, and the JVM
 * guarantees class initialisation happens exactly once under its own lock. You
 * get laziness for free and pay nothing on the fast path.
 *
 * If you are writing a lazy singleton in Java and it does not need to be an
 * enum, write this one. Being able to say why it beats double-checked locking
 * is a better signal than being able to write double-checked locking.
 */
public class LazyHolderConfig {

    private LazyHolderConfig() {
        System.out.println("      LazyHolderConfig constructed (on first get)");
    }

    private static class Holder {
        static final LazyHolderConfig INSTANCE = new LazyHolderConfig();
    }

    public static LazyHolderConfig get() {
        return Holder.INSTANCE;
    }
}
