import java.time.Duration;
import java.time.Instant;

public class Demo {

    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private static final Address HOME = new Address("42 Indiranagar 1st Main", "560038");

    public static void main(String[] args) {
        System.out.println("== A valid order ==");
        Order order = new Order.Builder("cust-77")
                .clockAt(NOW)
                .addItem("Hyderabadi biryani", 32_000, 2)
                .addItem("Gulab jamun", 8_000, 1)
                .deliverTo(HOME)
                .withCoupon("SAVE50")
                .scheduledFor(NOW.plus(Duration.ofHours(2)))
                .contactless()
                .withNote("Ring the bell twice")
                .build();
        System.out.println("  " + order);

        System.out.println();
        System.out.println("== Optional means optional — same class, three fields set ==");
        System.out.println("  " + new Order.Builder("cust-12")
                .clockAt(NOW)
                .addItem("Filter coffee", 6_000, 1)
                .deliverTo(HOME)
                .build());

        System.out.println();
        System.out.println("== Every failure happens at build(), before an Order exists ==");

        refused("no items", () -> new Order.Builder("cust-01")
                .clockAt(NOW).deliverTo(HOME).build());

        refused("no address", () -> new Order.Builder("cust-01")
                .clockAt(NOW).addItem("Vada pav", 4_000, 1).build());

        refused("coupon below the cart minimum", () -> new Order.Builder("cust-01")
                .clockAt(NOW).addItem("Vada pav", 4_000, 1)
                .deliverTo(HOME).withCoupon("SAVE50").build());

        refused("scheduled too soon", () -> new Order.Builder("cust-01")
                .clockAt(NOW).addItem("Biryani", 32_000, 1).deliverTo(HOME)
                .scheduledFor(NOW.plus(Duration.ofMinutes(10))).build());

        System.out.println();
        System.out.println("  The last two are cross-field invariants: the coupon rule needs");
        System.out.println("  the items, the lead-time rule needs the clock. Neither can be");
        System.out.println("  checked in a setter, because when withCoupon() runs there may");
        System.out.println("  be no items yet. That is the argument for a builder, in one line.");

        System.out.println();
        System.out.println("== And the built object is frozen ==");
        try {
            order.items().add(new OrderItem("Sneaky extra", 1_000, 1));
        } catch (UnsupportedOperationException e) {
            System.out.println("  items() is unmodifiable — no post-validation edits");
        }
    }

    private static void refused(String label, Runnable attempt) {
        try {
            attempt.run();
            System.out.println("  " + label + ": NOT refused — that's a bug in the builder");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }
}
