import java.util.List;

/**
 * A PaymentGateway made of PaymentGateways.
 *
 * This is the resilience argument, and it is worth making unprompted because it
 * is unusual to hear from an LLD candidate: because every provider hides behind
 * one interface, adding failover is a class that implements that interface and
 * no business code changes at all.
 *
 * Structurally this is a decorator over a list — see lld/08-decorator. It is
 * also the one place a routing DECISION is allowed to live, which is exactly
 * why it is here and not inside RazorpayAdapter.
 */
public class FailoverGateway implements PaymentGateway {

    private final List<PaymentGateway> providers;

    public FailoverGateway(List<PaymentGateway> providers) {
        if (providers.isEmpty()) throw new IllegalArgumentException("need at least one provider");
        this.providers = List.copyOf(providers);
    }

    @Override
    public String name() { return "failover"; }

    @Override
    public ChargeResult charge(Money amount, Instrument instrument, IdempotencyKey key) {
        ChargeResult last = null;

        for (PaymentGateway provider : providers) {
            if (!provider.isHealthy()) {
                System.out.println("      skipping " + provider.name() + ", health check failed");
                continue;
            }

            // The key is namespaced per provider. Reusing one key across two
            // providers is meaningless — idempotency is enforced by each
            // provider's own store — and namespacing it keeps your own
            // reconciliation honest about which attempt went where.
            IdempotencyKey scoped = new IdempotencyKey(provider.name() + ":" + key.value());
            last = provider.charge(amount, instrument, scoped);

            if (last.isSuccess()) {
                return last;
            }
            if (!last.isRetryableElsewhere()) {
                // A decline is a decline everywhere. Retrying it on a second
                // provider is how you turn one declined payment into two
                // suspicious authorisation attempts on a customer's card.
                return last;
            }
            System.out.println("      " + provider.name() + " returned "
                    + last.status() + ", trying the next one");
        }

        return last != null ? last
                : ChargeResult.providerError("no healthy provider available", name());
    }

    @Override
    public boolean isHealthy() {
        return providers.stream().anyMatch(PaymentGateway::isHealthy);
    }
}
