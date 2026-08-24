public class Demo {

    public static void main(String[] args) {
        syncBasics();
        oneThatThrows();
        unsubscribeFromInsideCallback();
        asyncWithBackpressure();
    }

    // ------------------------------------------------------------------

    private static void syncBasics() {
        System.out.println("== Synchronous topic: the publisher knows none of its consumers ==");
        Topic orders = new Topic("orders");

        orders.subscribe(named("email",     e -> System.out.println("    email:     confirmation for " + e.payload())));
        orders.subscribe(named("analytics", e -> System.out.println("    analytics: counted " + e.type())));
        orders.subscribe(named("inventory", e -> System.out.println("    inventory: decremented for " + e.payload())));

        orders.publish(new Event("order.placed", "ORD-1", 1));
        System.out.println("  adding a fourth consumer touched nothing in Topic:");
        orders.subscribe(named("fraud",     e -> System.out.println("    fraud:     scored " + e.payload())));
        orders.publish(new Event("order.placed", "ORD-2", 2));
        System.out.println();
    }

    private static void oneThatThrows() {
        System.out.println("== One subscriber throws — the others still run ==");
        Topic orders = new Topic("orders");
        orders.subscribe(named("email",     e -> System.out.println("    email:     ok")));
        orders.subscribe(named("brokenSms", e -> { throw new IllegalStateException("gateway 503"); }));
        orders.subscribe(named("analytics", e -> System.out.println("    analytics: ok")));

        orders.publish(new Event("order.placed", "ORD-3", 3));
        System.out.println("  " + orders.failureCount() + " failure, "
                + orders.subscriberCount() + " subscribers still registered");
        System.out.println();
    }

    private static void unsubscribeFromInsideCallback() {
        System.out.println("== A subscriber that unsubscribes from inside its own callback ==");
        Topic orders = new Topic("orders");

        // This is what CopyOnWriteArrayList buys you. With a plain ArrayList
        // the publish loop below throws ConcurrentModificationException.
        Subscriber oneShot = new Subscriber() {
            @Override
            public void onEvent(Event e) {
                System.out.println("    oneShot:  handled " + e + ", now unsubscribing");
                orders.unsubscribe(this);
            }
            @Override
            public String name() { return "oneShot"; }
        };

        orders.subscribe(oneShot);
        orders.subscribe(named("permanent", e -> System.out.println("    permanent: handled " + e)));

        orders.publish(new Event("order.placed", "ORD-4", 4));
        System.out.println("  subscribers now: " + orders.subscriberCount());
        orders.publish(new Event("order.placed", "ORD-5", 5));
        System.out.println();
    }

    private static void asyncWithBackpressure() {
        System.out.println("== Async: a slow subscriber, three overflow policies ==");
        System.out.println("  publishing 12 events; the slow consumers take 30ms each and");
        System.out.println("  their queues hold 3, so they cannot possibly keep up.");
        System.out.println();

        AsyncTopic clicks = new AsyncTopic("clicks");
        clicks.subscribe(named("fastMetrics", e -> { }),   3, OverflowPolicy.DROP_NEWEST);
        clicks.subscribe(named("slowMetrics", e -> sleep(30)), 3, OverflowPolicy.DROP_NEWEST);
        clicks.subscribe(named("slowAudit",   e -> sleep(30)), 3, OverflowPolicy.DEAD_LETTER);

        long started = System.nanoTime();
        for (int i = 1; i <= 12; i++) {
            clicks.publish(new Event("click", "ad-" + i, i));
        }
        System.out.println("  publish() returned in " + millisSince(started)
                + "ms — the publisher never waited for the slow consumers");
        clicks.close();
        clicks.report();

        System.out.println();
        System.out.println("  Now the same thing with BLOCK, which is the trade-off in reverse:");
        AsyncTopic payments = new AsyncTopic("payments");
        payments.subscribe(named("ledger", e -> sleep(30)), 3, OverflowPolicy.BLOCK);

        started = System.nanoTime();
        for (int i = 1; i <= 12; i++) {
            payments.publish(new Event("payment.captured", "PAY-" + i, i));
        }
        System.out.println("  publish() took " + millisSince(started)
                + "ms — nothing was lost, and the publisher paid for it");
        payments.close();
        payments.report();

        System.out.println();
        System.out.println("  Neither policy is right in general. DROP for metrics, BLOCK for");
        System.out.println("  the ledger, in the same system — that's the answer that reads as");
        System.out.println("  production experience rather than pattern recall.");
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    // ------------------------------------------------------------------

    private static Subscriber named(String name, java.util.function.Consumer<Event> body) {
        return new Subscriber() {
            @Override public void onEvent(Event e) { body.accept(e); }
            @Override public String name()         { return name; }
        };
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
