/**
 * Facade: one simple door onto a subsystem with four moving parts.
 *
 * The test that it is a real facade rather than just another class: callers can
 * still reach past it when they need to. A facade that hides its subsystem
 * completely is a rewrite, and it will grow every method the subsystem had.
 */
public class FacadeDemo {

    static class Inventory  { boolean reserve(String sku) { return true; } }
    static class Payments   { String charge(long paise)   { return "pay_1"; } }
    static class Shipping   { String schedule(String sku) { return "ship_1"; } }
    static class Notifier   { void confirm(String to)     { } }

    /** The facade. Four calls in the right order, behind one method. */
    static class Checkout {
        private final Inventory inventory = new Inventory();
        private final Payments payments = new Payments();
        private final Shipping shipping = new Shipping();
        private final Notifier notifier = new Notifier();

        String placeOrder(String sku, long paise, String email) {
            if (!inventory.reserve(sku)) return "out of stock";
            String payment = payments.charge(paise);
            String shipment = shipping.schedule(sku);
            notifier.confirm(email);
            return "ordered: " + payment + " / " + shipment;
        }

        /** Still reachable, on purpose. */
        Payments payments() { return payments; }
    }

    public static void show() {
        Checkout checkout = new Checkout();
        System.out.println("    " + checkout.placeOrder("biryani-2kg", 45_000, "a@b.com"));
        System.out.println("    and the subsystem is still reachable: "
                + checkout.payments().charge(100));
        System.out.println("    You have written dozens of these without naming it.");
    }
}
