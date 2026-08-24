public class StripeFactory implements PaymentProviderFactory {
    private final String apiKey;
    private final String signingSecret;

    public StripeFactory(String apiKey, String signingSecret) {
        this.apiKey = apiKey;
        this.signingSecret = signingSecret;
    }

    @Override public String name()              { return "stripe"; }
    @Override public Charger charger()          { return new StripeCharger(apiKey); }
    @Override public Refunder refunder()        { return new StripeRefunder(apiKey); }
    @Override public WebhookVerifier verifier() { return new StripeVerifier(signingSecret); }
}

class StripeCharger implements Charger {
    private final String apiKey;
    StripeCharger(String apiKey) { this.apiKey = apiKey; }

    @Override
    public String charge(String orderId, long amountPaise) {
        String paymentId = "pi_stripe_" + orderId;
        System.out.println("  stripe[" + apiKey + "] charged " + amountPaise
                + " paise for " + orderId + " -> " + paymentId);
        return paymentId;
    }
}

class StripeRefunder implements Refunder {
    private final String apiKey;
    StripeRefunder(String apiKey) { this.apiKey = apiKey; }

    @Override
    public void refund(String paymentId, long amountPaise) {
        System.out.println("  stripe[" + apiKey + "] refunded " + amountPaise
                + " paise against " + paymentId);
    }
}

class StripeVerifier implements WebhookVerifier {
    private final String signingSecret;
    StripeVerifier(String signingSecret) { this.signingSecret = signingSecret; }

    @Override
    public boolean verify(String payload, String signature) {
        return signature.equals("stripe:" + signingSecret + ":" + payload.length());
    }
}
