/**
 * The one interface the rest of the codebase talks to.
 *
 * Note what it does NOT have: a retry policy, a discount rule, a decision about
 * which provider to use. Those live above this line. An adapter that grows
 * business logic stops being an adapter and starts being a place where the same
 * rule gets duplicated once per provider.
 */
public interface PaymentGateway {

    String name();

    ChargeResult charge(Money amount, Instrument instrument, IdempotencyKey key);

    boolean isHealthy();
}
