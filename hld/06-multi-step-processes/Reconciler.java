import java.util.ArrayList;
import java.util.List;

/**
 * The job nobody puts on the diagram and every payments team runs.
 *
 * At scale a small fraction of sagas will always end up inconsistent: a
 * compensation that could not reach its service, an orchestrator that died
 * between two steps, a response that was lost rather than never sent. The right
 * response is not a cleverer protocol. It is a process that goes looking for the
 * broken records, because a design that assumes they cannot exist has simply
 * decided not to find them.
 *
 * This one looks for the specific shape that scares people: money captured, order
 * not confirmed. In a real system it would also run the other way round -
 * confirmed orders with no payment - and it would reconcile against the provider's
 * settlement file rather than only against your own tables, because the provider
 * is the authority on what was actually charged.
 */
public final class Reconciler {

    /** @return a human-readable line per repair. */
    public List<String> run(Database db) {
        List<String> repairs = new ArrayList<>();

        for (String orderId : db.orderIds()) {
            String payment = db.paymentState(orderId);
            String order = db.orderState(orderId);

            if ("CAPTURED".equals(payment) && !"CONFIRMED".equals(order)) {
                // Two defensible repairs, and which one you pick is a product
                // decision rather than an engineering one. Refund and cancel is
                // safest when you may not be able to deliver. Driving forward is
                // right when the goods are reserved and the only thing that
                // failed was a status update, which is the case here - and it is
                // also the one customers prefer, because their money moved and
                // they expect an order at the end of it.
                db.commit(() -> db.setOrderState(orderId, "CONFIRMED"), "order.confirmed", orderId);
                repairs.add(orderId + ": payment CAPTURED but order was " + order
                        + " - driven forward to CONFIRMED");
            }
        }
        return repairs;
    }
}
