import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Stands in for Kafka, or any broker.
 *
 * Deliberately at-least-once, because that is what you get. A broker that did
 * not see your acknowledgement has two options - send it again, or risk losing
 * it - and every broker worth using picks send it again. Exactly-once as a
 * transport property is marketing; what exists is at-least-once delivery plus
 * consumers that can absorb a duplicate without doing the work twice.
 */
public final class EventBus {

    public record Message(String messageId, String eventType, String payload) {
    }

    private final List<Consumer<Message>> subscribers = new ArrayList<>();
    private final List<Message> delivered = new ArrayList<>();

    public void subscribe(Consumer<Message> subscriber) {
        subscribers.add(subscriber);
    }

    public void publish(Message message) {
        delivered.add(message);
        for (Consumer<Message> subscriber : subscribers) {
            subscriber.accept(message);
        }
    }

    public int deliveredCount() {
        return delivered.size();
    }

    public List<Message> delivered() {
        return List.copyOf(delivered);
    }
}
