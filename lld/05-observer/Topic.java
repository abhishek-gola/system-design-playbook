import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The synchronous version, and the one to write first in an interview.
 *
 * Two deliberate choices worth narrating as you write them:
 *
 * CopyOnWriteArrayList — a subscriber can unsubscribe from inside its own
 * onEvent() without a ConcurrentModificationException. Reads are lock-free,
 * writes copy the array, which is the right trade when subscriptions are rare
 * and publishes are frequent.
 *
 * try/catch per subscriber — one broken handler must not cancel the rest.
 * This is the cheapest of the three fixes for "what if a subscriber
 * misbehaves", and there is no reason not to have it even here.
 */
public class Topic {
    private final String name;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private int failures;

    public Topic(String name) {
        this.name = name;
    }

    public void subscribe(Subscriber s)   { subscribers.add(s); }
    public void unsubscribe(Subscriber s) { subscribers.remove(s); }

    public void publish(Event event) {
        for (Subscriber s : subscribers) {
            try {
                s.onEvent(event);
            } catch (RuntimeException e) {
                failures++;
                System.out.println("    ! " + s.name() + " threw on " + event
                        + ": " + e.getMessage() + " (others continue)");
            }
        }
    }

    public String name()          { return name; }
    public int subscriberCount()  { return subscribers.size(); }
    public int failureCount()     { return failures; }
}
