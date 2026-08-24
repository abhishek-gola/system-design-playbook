import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A payment saga, end to end.
 *
 * Reserve inventory, authorise the card, capture, update the order, notify the
 * customer. Five local transactions across services you do not all own, and any
 * one of them can fail or, worse, time out ambiguously. There is no distributed
 * transaction available. What there is: forward steps, compensating steps, an
 * outbox so events cannot disagree with the database, idempotency keys so retries
 * are free, and a reconciliation job for the fraction that still ends up wrong.
 */
public final class Demo {

    public static void main(String[] args) {
        happyPath();
        failureAtStepFour();
        outboxVersusDirectPublish();
        idempotency();
        compensationFailsThenReconcile();
    }

    // ------------------------------------------------------------------ act 1

    private static void happyPath() {
        section("1. The happy path");
        String orderId = "order-1001";

        Database db = new Database(Steps.SKU, 5);
        PaymentGateway gateway = new PaymentGateway();
        EventBus bus = new EventBus();
        OutboxRelay relay = new OutboxRelay(db, bus);

        db.commit(() -> db.setOrderState(orderId, "PENDING"), "order.created", orderId);
        SagaContext ctx = new SagaContext(orderId);
        boolean ok = orchestratorFor(db, gateway, new Steps.UpdateOrder(db)).run(ctx);

        System.out.println();
        System.out.println("    saga completed: " + ok);
        printState(db, gateway, orderId);

        System.out.println();
        System.out.println("    the outbox now holds " + db.unpublished().size() + " unpublished rows.");
        System.out.println("    relay pass publishes " + relay.pump() + " of them:");
        for (EventBus.Message message : bus.delivered()) {
            System.out.println("      " + message.messageId() + "  " + message.eventType() + "  " + message.payload());
        }
    }

    // ------------------------------------------------------------------ act 2

    private static void failureAtStepFour() {
        section("2. Step four fails, compensations run in reverse");
        String orderId = "order-1002";

        Database db = new Database(Steps.SKU, 5);
        PaymentGateway gateway = new PaymentGateway();
        Steps.UpdateOrder updateOrder = new Steps.UpdateOrder(db);
        updateOrder.failNextRun(true);

        db.commit(() -> db.setOrderState(orderId, "PENDING"), "order.created", orderId);
        SagaContext ctx = new SagaContext(orderId);
        boolean ok = orchestratorFor(db, gateway, updateOrder).run(ctx);

        // The coordinator records the terminal state of the saga itself. The
        // update-order step never ran, so there is nothing of its own to undo,
        // but the customer still needs to see a cancelled order rather than one
        // stuck at PENDING forever.
        db.commit(() -> db.setOrderState(orderId, "CANCELLED"), "order.cancelled", orderId);

        System.out.println();
        System.out.println("    saga completed: " + ok);
        printState(db, gateway, orderId);
        System.out.println("    refunds issued: " + gateway.refunds());
        System.out.println();
        System.out.println("    Reverse order matters. Undoing the authorisation before the capture would");
        System.out.println("    have tried to void something that had already been taken. And notice the");
        System.out.println("    payment did not end at VOIDED: once money has moved the only compensation");
        System.out.println("    is a refund, which is a new line on the customer's statement rather than");
        System.out.println("    an erasure of the old one.");
    }

    // ------------------------------------------------------------------ act 3

    private static void outboxVersusDirectPublish() {
        section("3. The outbox, and what breaks without it");
        System.out.println("    Writing to your database and publishing to Kafka cannot be one atomic act.");
        System.out.println("    Whichever you do first, the process can die in between.");
        System.out.println();

        System.out.println("    (a) commit, then publish - and the process dies in the gap");
        Database direct = new Database(Steps.SKU, 5);
        EventBus directBus = new EventBus();
        direct.commitWithoutEvent(() -> direct.adjustUnits(Steps.SKU, -1));
        // The publish call would go here. It never happens.
        System.out.println("        units on hand    : " + direct.unitsOf(Steps.SKU) + "  (the reservation is real)");
        System.out.println("        events delivered : " + directBus.deliveredCount() + "  (nobody downstream will ever know)");
        System.out.println("        The warehouse never picks the item. There is no retry that helps, because");
        System.out.println("        the thing that knew an event was owed died with the process.");
        System.out.println();
        System.out.println("        Doing it the other way round is not better: publish first and the commit");
        System.out.println("        fails, and now there is an event describing a reservation that does not exist.");
        System.out.println();

        System.out.println("    (b) same crash, with the outbox");
        Database outboxed = new Database(Steps.SKU, 5);
        EventBus outboxBus = new EventBus();
        OutboxRelay relay = new OutboxRelay(outboxed, outboxBus);
        outboxed.commit(() -> outboxed.adjustUnits(Steps.SKU, -1), "inventory.reserved", "order-1003");
        System.out.println("        units on hand      : " + outboxed.unitsOf(Steps.SKU));
        System.out.println("        unpublished rows   : " + outboxed.unpublished().size() + "  (written in the same transaction)");
        System.out.println("        ...process restarts, relay wakes up...");
        System.out.println("        published this pass: " + relay.pump());
        System.out.println("        events delivered   : " + outboxBus.deliveredCount());
        System.out.println();
        System.out.println("        The event is published exactly when the business change committed, and");
        System.out.println("        never otherwise. That is the whole guarantee, and it is worth naming the");
        System.out.println("        pattern rather than describing it - most people recognise the word.");
    }

    // ------------------------------------------------------------------ act 4

    private static void idempotency() {
        section("4. Idempotency keys, and dedup at the consumer");
        String orderId = "order-1004";

        Database db = new Database(Steps.SKU, 5);
        PaymentGateway gateway = new PaymentGateway();
        SagaContext ctx = new SagaContext(orderId);
        SagaStep authorise = new Steps.AuthoriseCard(db, gateway);

        System.out.println("    The call times out. You cannot tell whether it arrived, so you retry it.");
        authorise.execute(ctx);
        System.out.println("    after the first call   : charges at the provider = " + gateway.actualAuthorisations());
        authorise.execute(ctx);
        authorise.execute(ctx);
        System.out.println("    after two more retries : charges at the provider = " + gateway.actualAuthorisations());
        for (String note : ctx.notes()) {
            System.out.println("      " + note);
        }
        System.out.println();
        System.out.println("    The key is " + ctx.idempotencyKey("authorise-card") + " - derived from the order and");
        System.out.println("    the step, not generated per attempt. A random key would make every retry a");
        System.out.println("    fresh charge, which is the bug the mechanism exists to prevent.");

        System.out.println();
        System.out.println("    The same idea at the consumer, because delivery is at-least-once:");
        EventBus bus = new EventBus();
        Set<String> seenMessageIds = new HashSet<>();
        int[] emailsSent = {0};
        bus.subscribe(message -> {
            if (seenMessageIds.add(message.messageId())) {
                emailsSent[0]++;
            }
        });
        EventBus.Message redelivered = new EventBus.Message("outbox-7", "customer.notify", orderId);
        bus.publish(redelivered);
        bus.publish(redelivered);
        bus.publish(redelivered);
        System.out.println("      deliveries received: " + bus.deliveredCount());
        System.out.println("      emails actually sent: " + emailsSent[0]);
        System.out.println("      Exactly-once is a property of the consumer, not of the transport.");
    }

    // ------------------------------------------------------------------ act 5

    private static void compensationFailsThenReconcile() {
        section("5. The compensation fails too, and reconciliation repairs it");
        String orderId = "order-1005";

        Database db = new Database(Steps.SKU, 5);
        PaymentGateway gateway = new PaymentGateway();
        EventBus bus = new EventBus();
        OutboxRelay relay = new OutboxRelay(db, bus);
        Steps.UpdateOrder updateOrder = new Steps.UpdateOrder(db);

        updateOrder.failNextRun(true);
        gateway.setRefundsFailing(true); // the provider is having the same bad afternoon

        db.commit(() -> db.setOrderState(orderId, "PENDING"), "order.created", orderId);
        SagaContext ctx = new SagaContext(orderId);
        orchestratorFor(db, gateway, updateOrder).run(ctx);

        System.out.println();
        System.out.println("    the record is now inconsistent:");
        printState(db, gateway, orderId);
        System.out.println("    Money captured, order not confirmed. This is the state the interviewer will");
        System.out.println("    ask about, and the honest answer is that no protocol prevents it.");

        System.out.println();
        System.out.println("    ...the reconciliation job runs...");
        List<String> repairs = new Reconciler().run(db);
        for (String repair : repairs) {
            System.out.println("      " + repair);
        }
        System.out.println("    repairs made: " + repairs.size());
        printState(db, gateway, orderId);
        System.out.println("    relay publishes the repair's events: " + relay.pump()
                + " (total delivered " + bus.deliveredCount() + ")");
        System.out.println();
        System.out.println("    Running it again finds nothing, because the repair left the record in a");
        System.out.println("    state the query no longer matches. A reconciler that is not idempotent is");
        System.out.println("    a reconciler nobody will dare schedule.");
        System.out.println("    second pass repairs: " + new Reconciler().run(db).size());
    }

    // ------------------------------------------------------------------ shared

    private static SagaOrchestrator orchestratorFor(Database db, PaymentGateway gateway, Steps.UpdateOrder updateOrder) {
        return new SagaOrchestrator(List.of(
                new Steps.ReserveInventory(db),
                new Steps.AuthoriseCard(db, gateway),
                new Steps.CaptureFunds(db, gateway),
                updateOrder,
                new Steps.NotifyCustomer(db)));
    }

    private static void printState(Database db, PaymentGateway gateway, String orderId) {
        System.out.println("    order " + orderId + ": " + db.orderState(orderId)
                + " | payment " + db.paymentState(orderId)
                + " | units on hand " + db.unitsOf(Steps.SKU)
                + " | captures " + gateway.actualCaptures());
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " " + "=".repeat(Math.max(0, 72 - title.length())));
        System.out.println();
    }
}
