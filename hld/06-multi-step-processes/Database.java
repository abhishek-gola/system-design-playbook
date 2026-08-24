import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One service's local database, plus its outbox table.
 *
 * The maps stand in for tables. What matters is not the storage, it is commit():
 * the business change and the event row go in together or not at all, because in
 * a real database they are one transaction. That single property is what the
 * outbox pattern buys, and it is why the event can never describe something that
 * did not happen, and the thing that happened can never fail to produce an event.
 *
 *   BEGIN;
 *     UPDATE inventory SET units = units - 1 WHERE sku = ?;
 *     INSERT INTO outbox (event_type, payload) VALUES (?, ?);
 *   COMMIT;
 */
public final class Database {

    /** A row in the outbox table. Immutable; publishing replaces it. */
    public record OutboxRow(long id, String eventType, String payload, boolean published) {
    }

    private final Map<String, String> orders = new LinkedHashMap<>();     // orderId -> PENDING / CONFIRMED / CANCELLED
    private final Map<String, String> payments = new LinkedHashMap<>();   // orderId -> AUTHORISED / CAPTURED / REFUNDED / VOIDED
    private final Map<String, Integer> inventory = new LinkedHashMap<>(); // sku -> units on hand
    private final List<OutboxRow> outbox = new ArrayList<>();

    private long nextOutboxId = 1;

    public Database(String sku, int units) {
        inventory.put(sku, units);
    }

    /** The business change and the event, committed together. */
    public void commit(Runnable businessWrite, String eventType, String payload) {
        businessWrite.run();
        outbox.add(new OutboxRow(nextOutboxId++, eventType, payload, false));
    }

    /** A local write with no event. Used by the demo to show what breaks without the outbox. */
    public void commitWithoutEvent(Runnable businessWrite) {
        businessWrite.run();
    }

    public void setOrderState(String orderId, String state) {
        orders.put(orderId, state);
    }

    public String orderState(String orderId) {
        return orders.getOrDefault(orderId, "NONE");
    }

    public void setPaymentState(String orderId, String state) {
        payments.put(orderId, state);
    }

    public String paymentState(String orderId) {
        return payments.getOrDefault(orderId, "NONE");
    }

    public void adjustUnits(String sku, int delta) {
        inventory.merge(sku, delta, Integer::sum);
    }

    public int unitsOf(String sku) {
        return inventory.getOrDefault(sku, 0);
    }

    public List<String> orderIds() {
        return new ArrayList<>(orders.keySet());
    }

    /** A copy, so the relay can mark rows published while iterating. */
    public List<OutboxRow> unpublished() {
        List<OutboxRow> pending = new ArrayList<>();
        for (OutboxRow row : outbox) {
            if (!row.published()) {
                pending.add(row);
            }
        }
        return pending;
    }

    public void markPublished(long id) {
        for (int i = 0; i < outbox.size(); i++) {
            OutboxRow row = outbox.get(i);
            if (row.id() == id) {
                outbox.set(i, new OutboxRow(row.id(), row.eventType(), row.payload(), true));
                return;
            }
        }
    }

    public List<OutboxRow> allOutboxRows() {
        return List.copyOf(outbox);
    }
}
