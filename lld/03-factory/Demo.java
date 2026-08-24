import java.util.List;

public class Demo {

    public static void main(String[] args) {
        System.out.println("== Simple factory: the switch, and it is fine ==");
        NotifierFactory notifiers = new NotifierFactory("smtp.internal", "msg91", "com.swiggy.app");
        Message m = new Message("abhishek", "Order confirmed", "Your biryani is 12 minutes away");
        for (Channel c : Channel.values()) {
            notifiers.of(c).send(m);
        }

        System.out.println();
        System.out.println("== Registry variant: same result, and a new channel needs no recompile ==");
        for (Channel c : Channel.values()) {
            notifiers.fromRegistry(c).send(m);
        }
        notifiers.register(Channel.SMS, () -> new SmsNotifier("kaleyra-failover"));
        System.out.println("  after swapping the SMS provider at runtime:");
        notifiers.fromRegistry(Channel.SMS).send(m);

        System.out.println();
        System.out.println("== Abstract factory: one family at a time ==");
        List<PaymentProviderFactory> providers = List.of(
                new RazorpayFactory("rzp_live_a1", "secret-r"),
                new StripeFactory("sk_live_b2", "secret-s"));

        String payload = "{\"event\":\"payment.captured\"}";

        for (PaymentProviderFactory provider : providers) {
            System.out.println("  --- " + provider.name() + " ---");
            String paymentId = provider.charger().charge("ORD-9001", 45_000);
            provider.refunder().refund(paymentId, 45_000);

            String goodSignature = provider.name().equals("razorpay")
                    ? "rzp:secret-r:" + payload.length()
                    : "stripe:secret-s:" + payload.length();

            System.out.println("  webhook verified: "
                    + provider.verifier().verify(payload, goodSignature));
        }

        System.out.println();
        System.out.println("== What the abstract factory is preventing ==");
        PaymentProviderFactory razorpay = providers.get(0);
        PaymentProviderFactory stripe = providers.get(1);

        // You have to go out of your way to build this mismatch. In a codebase
        // that wires each object separately from config, it happens by accident
        // during a provider migration and nobody notices until callbacks stop.
        Charger razorpayCharger = razorpay.charger();
        WebhookVerifier stripeVerifier = stripe.verifier();

        String paymentId = razorpayCharger.charge("ORD-9002", 12_000);
        String razorpaySignature = "rzp:secret-r:" + payload.length();
        System.out.println("  razorpay charged " + paymentId
                + ", then stripe's verifier saw the callback:");
        System.out.println("  verified: " + stripeVerifier.verify(payload, razorpaySignature)
                + "   <- every confirmation silently dropped");
        System.out.println();
        System.out.println("  Taking the whole family from one factory makes that");
        System.out.println("  combination impossible to express. That is the only reason");
        System.out.println("  abstract factory is worth its ceremony — no consistency");
        System.out.println("  requirement, no abstract factory.");
    }
}
