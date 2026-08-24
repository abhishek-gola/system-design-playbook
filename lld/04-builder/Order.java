import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Immutable once built. No setters, all fields final, the item list defensively
 * copied on the way in and unmodifiable on the way out.
 *
 * The private constructor is the enforcement: the only route to an Order is
 * through Builder.build(), which means the only route to an Order is through
 * validation.
 */
public final class Order {

    private final String customerId;
    private final List<OrderItem> items;
    private final Address deliverTo;
    private final String couponCode;
    private final Instant scheduledFor;
    private final String note;
    private final boolean contactlessDelivery;

    private Order(Builder b) {
        this.customerId = b.customerId;
        this.items = List.copyOf(b.items);
        this.deliverTo = b.deliverTo;
        this.couponCode = b.couponCode;
        this.scheduledFor = b.scheduledFor;
        this.note = b.note;
        this.contactlessDelivery = b.contactlessDelivery;
    }

    public String customerId()       { return customerId; }
    public List<OrderItem> items()   { return items; }
    public Address deliverTo()       { return deliverTo; }
    public String couponCode()       { return couponCode; }
    public Instant scheduledFor()    { return scheduledFor; }
    public String note()             { return note; }
    public boolean contactless()     { return contactlessDelivery; }

    public long subtotalPaise() {
        return items.stream().mapToLong(OrderItem::totalPaise).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Order[").append(customerId)
                .append(" ").append(items)
                .append(" -> ").append(deliverTo)
                .append(" subtotal ").append(OrderItem.rupees(subtotalPaise()));
        if (couponCode != null)   sb.append(" coupon ").append(couponCode);
        if (scheduledFor != null) sb.append(" at ").append(scheduledFor);
        if (contactlessDelivery)  sb.append(" contactless");
        if (note != null)         sb.append(" note '").append(note).append('\'');
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------

    public static class Builder {
        /** Required, so it goes in the builder's own constructor. */
        private final String customerId;

        private final List<OrderItem> items = new ArrayList<>();
        private Address deliverTo;
        private String couponCode;
        private Instant scheduledFor;
        private String note;
        private boolean contactlessDelivery;

        /** Injected so build()'s time checks are testable without sleeping. */
        private Instant now = Instant.now();

        private static final long COUPON_MINIMUM_PAISE = 19_900;   // Rs 199
        private static final Duration MIN_SCHEDULE_LEAD = Duration.ofMinutes(30);

        public Builder(String customerId) {
            if (customerId == null || customerId.isBlank()) {
                throw new IllegalArgumentException("customerId is required");
            }
            this.customerId = customerId;
        }

        public Builder addItem(String name, long unitPricePaise, int quantity) {
            items.add(new OrderItem(name, unitPricePaise, quantity));
            return this;
        }

        public Builder deliverTo(Address address)     { this.deliverTo = address; return this; }
        public Builder withCoupon(String code)        { this.couponCode = code; return this; }
        public Builder scheduledFor(Instant when)     { this.scheduledFor = when; return this; }
        public Builder withNote(String note)          { this.note = note; return this; }
        public Builder contactless()                  { this.contactlessDelivery = true; return this; }
        public Builder clockAt(Instant now)           { this.now = now; return this; }

        /**
         * Every rule lives here, in one place, checked once, before the object
         * exists.
         *
         * Two of these are cross-field invariants — the coupon minimum needs the
         * items, and the lead time needs the clock. They CANNOT be checked in a
         * setter, because when withCoupon() runs there may be no items yet. That
         * is the argument for a builder in one sentence.
         */
        public Order build() {
            if (items.isEmpty()) {
                throw new IllegalStateException("an order needs at least one item");
            }
            if (deliverTo == null) {
                throw new IllegalStateException("an order needs a delivery address");
            }

            long subtotal = items.stream().mapToLong(OrderItem::totalPaise).sum();
            if (couponCode != null && subtotal < COUPON_MINIMUM_PAISE) {
                throw new IllegalStateException("coupon " + couponCode + " needs a cart of "
                        + OrderItem.rupees(COUPON_MINIMUM_PAISE) + ", this one is "
                        + OrderItem.rupees(subtotal));
            }
            if (scheduledFor != null && scheduledFor.isBefore(now.plus(MIN_SCHEDULE_LEAD))) {
                throw new IllegalStateException("scheduled slots must be at least "
                        + MIN_SCHEDULE_LEAD.toMinutes() + " minutes out");
            }

            return new Order(this);
        }
    }
}
