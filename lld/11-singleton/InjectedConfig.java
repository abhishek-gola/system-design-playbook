import java.util.Map;

/**
 * What you would actually build, and the answer to give after you have shown
 * you can write the other four.
 *
 * One instance still exists — the wiring code creates exactly one and passes it
 * around. The difference is that the constraint lives in the wiring rather than
 * in the class, so:
 *
 *   - nothing has a hidden dependency; a class that needs config says so in its
 *     constructor
 *   - two tests can each have their own instance and stop affecting each other
 *   - swapping it for a fake is a constructor argument, not a static hack
 *
 * This is what every dependency injection framework does. Spring beans are
 * singletons by default and none of them are the Singleton pattern, which is a
 * good line to have ready.
 */
public class InjectedConfig {

    private final Map<String, String> values;

    public InjectedConfig(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public String get(String key) {
        return values.get(key);
    }
}
