/**
 * The three implementation classes are package-private and live in this file
 * because they are useless apart from each other — which is the same fact the
 * abstract factory encodes.
 */
public class RazorpayFactory implements PaymentProviderFactory {
    private final String keyId;
    private final String signingSecret;

    public RazorpayFactory(String keyId, String signingSecret) {
        this.keyId = keyId;
        this.signingSecret = signingSecret;
    }

    @Override public String name()             { return "razorpay"; }
    @Override public Charger charger()         { return new RazorpayCharger(keyId); }
    @Override public Refunder refunder()       { return new RazorpayRefunder(keyId); }
    @Override public WebhookVerifier verifier() { return new RazorpayVerifier(signingSecret); }
}

class RazorpayCharger implements Charger {
    private final String keyId;
    RazorpayCharger(String keyId) { this.keyId = keyId; }

    @Override
    public String charge(String orderId, long amountPaise) {
        String paymentId = "pay_rzp_" + orderId;
        System.out.println("  razorpay[" + keyId + "] charged " + amountPaise
                + " paise for " + orderId + " -> " + paymentId);
        return paymentId;
    }
}

class RazorpayRefunder implements Refunder {
    private final String keyId;
    RazorpayRefunder(String keyId) { this.keyId = keyId; }

    @Override
    public void refund(String paymentId, long amountPaise) {
        System.out.println("  razorpay[" + keyId + "] refunded " + amountPaise
                + " paise against " + paymentId);
    }
}

class RazorpayVerifier implements WebhookVerifier {
    private final String signingSecret;
    RazorpayVerifier(String signingSecret) { this.signingSecret = signingSecret; }

    @Override
    public boolean verify(String payload, String signature) {
        // Stand-in for an HMAC. What matters is that it depends on this
        // provider's secret and no other's.
        return signature.equals("rzp:" + signingSecret + ":" + payload.length());
    }
}
