import java.util.Map;

/**
 * Translate in, call, translate out. Nothing else.
 *
 * The return trip is the half candidates skip. Handing the caller an RzpOrder
 * back would defeat the entire exercise — the vendor type would be in your
 * business logic and the migration you were protecting against becomes exactly
 * as expensive as it would have been without the adapter.
 */
public class RazorpayAdapter implements PaymentGateway {

    private final RazorpaySdk sdk;

    public RazorpayAdapter(RazorpaySdk sdk) {
        this.sdk = sdk;
    }

    @Override
    public String name() { return "razorpay"; }

    @Override
    public ChargeResult charge(Money amount, Instrument instrument, IdempotencyKey key) {
        Map<String, Object> options = sdk.newOptions();
        options.put("amount", (int) amount.minorUnits());       // paise, as an int
        options.put("currency", amount.currency());
        options.put("method_token", instrument.token());
        options.put("receipt", key.value());                    // their name for it

        try {
            RazorpaySdk.RzpOrder order = sdk.createOrder(options);
            return "captured".equals(order.status)
                    ? ChargeResult.captured(order.id, name())
                    : ChargeResult.declined("status " + order.status, name());
        } catch (RazorpaySdk.RzpException e) {
            return mapError(e);
        }
    }

    /**
     * The mapping table. An unmapped code becomes UNKNOWN rather than leaking
     * through as a string the caller has to interpret — because the day the
     * provider adds a code, you want a clean UNKNOWN in your metrics, not a
     * switch somewhere upstream falling through to "success".
     */
    private ChargeResult mapError(RazorpaySdk.RzpException e) {
        return switch (e.code) {
            case "BAD_REQUEST_ERROR" -> ChargeResult.declined(e.getMessage(), name());
            case "SERVER_ERROR", "GATEWAY_ERROR" -> ChargeResult.providerError(e.getMessage(), name());
            default -> ChargeResult.unknown(e.code, name());
        };
    }

    @Override
    public boolean isHealthy() {
        // A status endpoint, not a test charge. Health checks that cost money
        // are a real and surprisingly common mistake.
        return sdk.ping();
    }
}
