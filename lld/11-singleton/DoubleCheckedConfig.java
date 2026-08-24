/**
 * The version they ask about, and the volatile is the entire question.
 *
 * Without volatile, another thread can see a non-null reference to a PARTIALLY
 * CONSTRUCTED object. `instance = new DoubleCheckedConfig()` is really three
 * steps — allocate, run the constructor, publish the reference — and the JVM is
 * allowed to reorder the last two. A second thread then sees a non-null
 * instance whose fields are still zero.
 *
 * volatile inserts the happens-before edge that forbids the reordering. Before
 * Java 5 the memory model had no way to express that, which is why so much old
 * advice about this pattern is simply wrong.
 *
 * Knowing all that is worth a mark. Writing this in real code is not — see
 * LazyHolderConfig for the version that gets the same laziness with no
 * synchronisation at all.
 */
public class DoubleCheckedConfig {

    private static volatile DoubleCheckedConfig instance;

    private DoubleCheckedConfig() {
        System.out.println("      DoubleCheckedConfig constructed (on first get)");
    }

    public static DoubleCheckedConfig get() {
        DoubleCheckedConfig local = instance;      // one volatile read on the fast path
        if (local == null) {
            synchronized (DoubleCheckedConfig.class) {
                local = instance;
                if (local == null) {
                    instance = local = new DoubleCheckedConfig();
                }
            }
        }
        return local;
    }
}
