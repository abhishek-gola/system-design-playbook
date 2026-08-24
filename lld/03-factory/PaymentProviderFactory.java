/**
 * Abstract factory: a family of objects that must match each other.
 *
 * This earns its complexity for one reason and one reason only — mixing
 * families here would be a real bug. A Razorpay charger paired with a Stripe
 * verifier would check callback signatures against the wrong secret and
 * silently reject every payment confirmation you receive.
 *
 * The factory makes that combination unrepresentable. If your objects have no
 * such consistency requirement, use a simple factory and say why.
 */
public interface PaymentProviderFactory {
    String name();
    Charger charger();
    Refunder refunder();
    WebhookVerifier verifier();
}
