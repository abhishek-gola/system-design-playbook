/**
 * Same interface, completely different translation. Note that the unit
 * conversion happens here and nowhere else: Money knows how to render itself as
 * a decimal string, and this adapter is the only thing that asks it to.
 */
public class StripeAdapter implements PaymentGateway {

    private final StripeSdk sdk;

    public StripeAdapter(StripeSdk sdk) {
        this.sdk = sdk;
    }

    @Override
    public String name() { return "stripe"; }

    @Override
    public ChargeResult charge(Money amount, Instrument instrument, IdempotencyKey key) {
        StripeSdk.PaymentIntent intent = sdk.createIntent(
                amount.asDecimalString(),      // decimal string, not paise
                amount.currency(),
                instrument.token(),
                key.value());

        if ("succeeded".equals(intent.status)) {
            return ChargeResult.captured(intent.id, name());
        }
        return switch (intent.errorCode == null ? "" : intent.errorCode) {
            case "card_declined" -> ChargeResult.declined("card declined by issuer", name());
            case "api_error"     -> ChargeResult.providerError("stripe api error", name());
            default              -> ChargeResult.unknown(String.valueOf(intent.errorCode), name());
        };
    }

    @Override
    public boolean isHealthy() {
        return sdk.ping();
    }
}
