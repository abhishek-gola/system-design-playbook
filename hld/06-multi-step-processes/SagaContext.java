import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The state the saga carries between steps, and the thing an orchestrator
 * persists after every step.
 *
 * That persistence is the whole reason orchestration is easier to operate than
 * choreography. When a customer rings up and asks where their money is, you read
 * one row and answer. With choreography you read five services' logs and guess.
 */
public final class SagaContext {

    private final String orderId;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> notes = new ArrayList<>();

    public SagaContext(String orderId) {
        this.orderId = orderId;
    }

    public String orderId() {
        return orderId;
    }

    public void put(String key, String value) {
        values.put(key, value);
    }

    public String get(String key) {
        return values.get(key);
    }

    public void note(String line) {
        notes.add(line);
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    /**
     * The idempotency key for one step of one saga.
     *
     * The property that matters is that it is derived, not generated. If the key
     * were random, a retry would mint a fresh one and the downstream service
     * would happily charge the card a second time - which is the entire bug the
     * key exists to prevent. Derive it from the business identity of the work:
     * this order, this step. Retry a thousand times and it is still the same key.
     */
    public String idempotencyKey(String stepName) {
        return orderId + ":" + stepName;
    }
}
