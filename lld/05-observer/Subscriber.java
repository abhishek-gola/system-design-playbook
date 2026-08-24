/**
 * One method. That narrowness is what makes anything a subscriber — a lambda,
 * a method reference, an existing service with an adapter around it.
 */
public interface Subscriber {
    void onEvent(Event event);

    default String name() { return getClass().getSimpleName(); }
}
