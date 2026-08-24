/**
 * The five steps of the payment flow, each with its compensation.
 *
 * They live as nested classes in one file only to keep the folder readable -
 * in a real service each of these is a call to a different team's API, which is
 * the whole reason a distributed transaction is not available to you.
 *
 * Read them for two things. First, every compensation is safe to run twice:
 * either it checks a flag first or the underlying operation is naturally
 * repeatable. Second, every mutating call downstream carries an idempotency key
 * derived from the order and the step, so a retry after an ambiguous timeout is
 * a no-op rather than a second charge.
 */
public final class Steps {

    public static final String SKU = "SKU-42";
    public static final int AMOUNT_PENCE = 4_999;

    private Steps() {
    }

    /** Step 1. Local, ours, and the easiest thing in the saga to undo. */
    public static final class ReserveInventory implements SagaStep {

        private final Database db;

        public ReserveInventory(Database db) {
            this.db = db;
        }

        @Override
        public String name() {
            return "reserve-inventory";
        }

        @Override
        public void execute(SagaContext ctx) {
            if (db.unitsOf(SKU) <= 0) {
                throw new StepFailedException("no units left of " + SKU);
            }
            db.commit(() -> db.adjustUnits(SKU, -1), "inventory.reserved", ctx.orderId());
            ctx.put("reserved", "yes");
        }

        @Override
        public void compensate(SagaContext ctx) {
            // The guard is what makes this safe to call twice. Without it, a
            // retried compensation puts two units back and you have invented
            // stock that does not exist.
            if (!"yes".equals(ctx.get("reserved"))) {
                return;
            }
            db.commit(() -> db.adjustUnits(SKU, +1), "inventory.released", ctx.orderId());
            ctx.put("reserved", "no");
        }
    }

    /** Step 2. The first call that leaves the building. */
    public static final class AuthoriseCard implements SagaStep {

        private final Database db;
        private final PaymentGateway gateway;

        public AuthoriseCard(Database db, PaymentGateway gateway) {
            this.db = db;
            this.gateway = gateway;
        }

        @Override
        public String name() {
            return "authorise-card";
        }

        @Override
        public void execute(SagaContext ctx) {
            String key = ctx.idempotencyKey(name());
            boolean replay = gateway.hasSeen(key);
            String authId = gateway.authorise(key, ctx.orderId(), AMOUNT_PENCE);
            if (replay) {
                ctx.note("authorise replayed key " + key + ", no second charge");
            }
            ctx.put("authId", authId);
            db.commit(() -> db.setPaymentState(ctx.orderId(), "AUTHORISED"), "payment.authorised", ctx.orderId());
        }

        @Override
        public void compensate(SagaContext ctx) {
            String authId = ctx.get("authId");
            if (authId == null) {
                return;
            }
            // You can only void an authorisation that was never captured. Once
            // the money has moved, the refund in step 3's compensation is what
            // undoes it, and voiding here would overwrite a truthful payment
            // state with a false one. Compensations have to know what the later
            // steps did, which is one of the reasons the coordinator holds the
            // state rather than each service holding its own idea of it.
            String state = db.paymentState(ctx.orderId());
            if ("CAPTURED".equals(state) || "REFUNDED".equals(state)) {
                return;
            }
            gateway.voidAuthorisation(authId);
            db.commit(() -> db.setPaymentState(ctx.orderId(), "VOIDED"), "payment.voided", ctx.orderId());
        }
    }

    /** Step 3. After this one the money has actually moved, and compensation gets expensive. */
    public static final class CaptureFunds implements SagaStep {

        private final Database db;
        private final PaymentGateway gateway;

        public CaptureFunds(Database db, PaymentGateway gateway) {
            this.db = db;
            this.gateway = gateway;
        }

        @Override
        public String name() {
            return "capture-funds";
        }

        @Override
        public void execute(SagaContext ctx) {
            gateway.capture(ctx.idempotencyKey(name()), ctx.get("authId"));
            db.commit(() -> db.setPaymentState(ctx.orderId(), "CAPTURED"), "payment.captured", ctx.orderId());
        }

        @Override
        public void compensate(SagaContext ctx) {
            // A refund is a new transaction, not an undo. The customer sees both
            // lines on their statement, and if this throws there is no third
            // option - the record stays inconsistent until reconciliation.
            gateway.refund(ctx.get("authId"));
            db.commit(() -> db.setPaymentState(ctx.orderId(), "REFUNDED"), "payment.refunded", ctx.orderId());
        }
    }

    /** Step 4. Ours again, and in the demo the one that times out. */
    public static final class UpdateOrder implements SagaStep {

        private final Database db;
        private boolean failNext = false;

        public UpdateOrder(Database db) {
            this.db = db;
        }

        public void failNextRun(boolean fail) {
            this.failNext = fail;
        }

        @Override
        public String name() {
            return "update-order";
        }

        @Override
        public void execute(SagaContext ctx) {
            if (failNext) {
                // A timeout, not a rejection. Notice you cannot actually tell
                // whether the order service committed before it stopped
                // answering, which is why the repair job later has to look at
                // the data rather than trust this outcome.
                throw new StepFailedException("order service timed out");
            }
            db.commit(() -> db.setOrderState(ctx.orderId(), "CONFIRMED"), "order.confirmed", ctx.orderId());
        }

        @Override
        public void compensate(SagaContext ctx) {
            db.commit(() -> db.setOrderState(ctx.orderId(), "CANCELLED"), "order.cancelled", ctx.orderId());
        }
    }

    /** Step 5. Deliberately the last one, because it cannot be taken back. */
    public static final class NotifyCustomer implements SagaStep {

        private final Database db;

        public NotifyCustomer(Database db) {
            this.db = db;
        }

        @Override
        public String name() {
            return "notify-customer";
        }

        @Override
        public void execute(SagaContext ctx) {
            // The notification goes through the outbox rather than a direct call
            // to the email service. Ordering irreversible side effects last, and
            // sending them through the log, means a saga that fails earlier never
            // emails a customer about an order that is about to be cancelled.
            db.commit(() -> {
            }, "customer.notify", ctx.orderId());
        }

        @Override
        public void compensate(SagaContext ctx) {
            ctx.note("nothing to undo: you cannot unsend an email, you send a correction");
        }
    }
}
