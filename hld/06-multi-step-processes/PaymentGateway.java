import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The external payment provider. The one participant in the saga you do not own
 * and cannot roll back.
 *
 * Its interesting behaviour is the idempotency key. Send the same key twice and
 * you get the first answer back rather than a second charge. Every serious
 * payments API works this way - Stripe, Adyen, your bank's - and the reason is
 * that the network makes a timeout ambiguous. The caller cannot tell a request
 * that never arrived from a response that got lost, and it must retry either
 * way. The key is what makes retrying safe.
 */
public final class PaymentGateway {

    private final Map<String, String> resultsByKey = new LinkedHashMap<>();
    private final Set<String> capturedKeys = new LinkedHashSet<>();

    private int actualAuthorisations = 0;
    private int actualCaptures = 0;
    private int refunds = 0;
    private boolean refundsFailing = false;

    /** Simulates the provider being unreachable exactly when you need to undo something. */
    public void setRefundsFailing(boolean failing) {
        this.refundsFailing = failing;
    }

    public boolean hasSeen(String idempotencyKey) {
        return resultsByKey.containsKey(idempotencyKey);
    }

    public String authorise(String idempotencyKey, String orderId, int amountPence) {
        String previous = resultsByKey.get(idempotencyKey);
        if (previous != null) {
            // The retry path. No money moves, the original answer comes back.
            return previous;
        }
        actualAuthorisations++;
        String authId = "auth-" + orderId;
        resultsByKey.put(idempotencyKey, authId);
        return authId;
    }

    public void capture(String idempotencyKey, String authId) {
        if (capturedKeys.contains(idempotencyKey)) {
            return;
        }
        capturedKeys.add(idempotencyKey);
        actualCaptures++;
    }

    public void voidAuthorisation(String authId) {
        // Voiding an authorisation that was never captured costs nothing and is
        // always safe to repeat, which is what a good compensation looks like.
    }

    public void refund(String authId) {
        if (refundsFailing) {
            throw new StepFailedException("payment provider unreachable, refund not accepted");
        }
        refunds++;
    }

    public int actualAuthorisations() {
        return actualAuthorisations;
    }

    public int actualCaptures() {
        return actualCaptures;
    }

    public int refunds() {
        return refunds;
    }
}
