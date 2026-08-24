/**
 * The relay: reads unpublished outbox rows, publishes them, marks them done.
 *
 * In production this is either a poller like this one or change data capture
 * reading the database's write-ahead log, which is what Debezium does. CDC is
 * nicer because it adds no query load and no polling lag; a poller is nicer
 * because you can explain it to a new joiner in one sentence and debug it with
 * a SELECT.
 *
 * Either way the relay can crash between publishing and marking the row done,
 * so the same event gets published twice. That is fine and expected: this is why
 * consumers deduplicate on message id. The outbox guarantees at-least-once, and
 * it never promised anything else.
 */
public final class OutboxRelay {

    private final Database db;
    private final EventBus bus;

    public OutboxRelay(Database db, EventBus bus) {
        this.db = db;
        this.bus = bus;
    }

    /** @return how many events this pass published. */
    public int pump() {
        int published = 0;
        for (Database.OutboxRow row : db.unpublished()) {
            // The message id is the outbox row id, which is stable across
            // republishes. A random id here would defeat consumer-side dedup.
            bus.publish(new EventBus.Message("outbox-" + row.id(), row.eventType(), row.payload()));
            db.markPublished(row.id());
            published++;
        }
        return published;
    }
}
