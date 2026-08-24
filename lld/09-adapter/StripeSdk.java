/**
 * A second third-party SDK with a deliberately different shape: amounts as a
 * decimal string, results as a status object rather than an exception, and its
 * own error vocabulary.
 *
 * Two providers whose SDKs disagree about everything is the normal case, not a
 * contrived one, and it is why the mapping code has to live somewhere.
 */
public class StripeSdk {

    public static class PaymentIntent {
        public String id;
        public String status;       // "succeeded", "requires_payment_method", "error"
        public String errorCode;    // "card_declined", "api_error", or null
    }

    private final String apiKey;
    private boolean up = true;

    public StripeSdk(String apiKey) { this.apiKey = apiKey; }

    public void goDown() { up = false; }

    public boolean ping() { return up; }

    /** Amount as a decimal string, because of course it is. */
    public PaymentIntent createIntent(String amountDecimal, String currency,
                                      String source, String idempotencyKey) {
        PaymentIntent intent = new PaymentIntent();
        if (!up) {
            intent.status = "error";
            intent.errorCode = "api_error";
            return intent;
        }
        if (source.contains("declined")) {
            intent.status = "requires_payment_method";
            intent.errorCode = "card_declined";
            return intent;
        }
        intent.id = "pi_stripe_" + idempotencyKey;
        intent.status = "succeeded";
        return intent;
    }
}
