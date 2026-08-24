import java.util.List;

public class Demo {

    private static final Money AMOUNT = Money.rupees(450);
    private static final Instrument GOOD = new Instrument(Instrument.Kind.CARD, "tok-good-1");
    private static final Instrument DECLINED = new Instrument(Instrument.Kind.CARD, "tok-declined-2");
    private static final Instrument WEIRD = new Instrument(Instrument.Kind.CARD, "tok-weird-3");

    public static void main(String[] args) {
        RazorpaySdk razorpaySdk = new RazorpaySdk("rzp_live_a1");
        StripeSdk stripeSdk = new StripeSdk("sk_live_b2");

        PaymentGateway razorpay = new RazorpayAdapter(razorpaySdk);
        PaymentGateway stripe = new StripeAdapter(stripeSdk);

        System.out.println("== Two SDKs that agree about nothing, one interface ==");
        System.out.println("  amount " + AMOUNT
                + "   razorpay wants " + AMOUNT.minorUnits() + " paise as an int");
        System.out.println("  " + " ".repeat(15)
                + "   stripe wants \"" + AMOUNT.asDecimalString() + "\" as a string");
        System.out.println("  razorpay: " + razorpay.charge(AMOUNT, GOOD, new IdempotencyKey("ORD-1")));
        System.out.println("  stripe:   " + stripe.charge(AMOUNT, GOOD, new IdempotencyKey("ORD-1")));

        System.out.println();
        System.out.println("== Two error vocabularies mapped onto one ==");
        System.out.println("  razorpay throws RzpException(BAD_REQUEST_ERROR)");
        System.out.println("    -> " + razorpay.charge(AMOUNT, DECLINED, new IdempotencyKey("ORD-2")));
        System.out.println("  stripe returns status=requires_payment_method, code=card_declined");
        System.out.println("    -> " + stripe.charge(AMOUNT, DECLINED, new IdempotencyKey("ORD-2")));
        System.out.println("  Same domain status from two completely different shapes.");

        System.out.println();
        System.out.println("== A vendor code nobody has mapped yet ==");
        System.out.println("  " + razorpay.charge(AMOUNT, WEIRD, new IdempotencyKey("ORD-3")));
        System.out.println("  UNKNOWN, not a leaked string. The day a provider adds a code you");
        System.out.println("  want a clean bucket in your metrics, not a switch upstream");
        System.out.println("  falling through to success.");

        System.out.println();
        System.out.println("== Failover, and no business code changes ==");
        PaymentGateway gateway = new FailoverGateway(List.of(razorpay, stripe));
        OrderService orders = new OrderService(gateway);

        System.out.println("  both up:");
        System.out.println("    " + orders.placeOrder("ORD-4", AMOUNT, GOOD));

        razorpaySdk.goDown();
        System.out.println("  razorpay down:");
        System.out.println("    " + orders.placeOrder("ORD-5", AMOUNT, GOOD));

        stripeSdk.goDown();
        System.out.println("  both down:");
        System.out.println("    " + orders.placeOrder("ORD-6", AMOUNT, GOOD));

        razorpaySdk.comeBack();
        System.out.println("  razorpay back, but the card is declined:");
        System.out.println("    " + orders.placeOrder("ORD-7", AMOUNT, DECLINED));
        System.out.println("    A decline is a decline everywhere, so failover does NOT retry");
        System.out.println("    it. Retrying a decline on a second provider turns one refused");
        System.out.println("    payment into two suspicious authorisations on a real card.");

        System.out.println();
        System.out.println("== The test seam: an order test with no network at all ==");
        FakeGateway fake = new FakeGateway()
                .willReturn(ChargeResult.captured("pay_test_1", "fake"))
                .willReturn(ChargeResult.declined("insufficient funds", "fake"))
                .willReturn(ChargeResult.providerError("timeout", "fake"));

        OrderService underTest = new OrderService(fake);
        System.out.println("  happy path:  " + underTest.placeOrder("ORD-8", AMOUNT, GOOD));
        System.out.println("  declined:    " + underTest.placeOrder("ORD-9", AMOUNT, GOOD));
        System.out.println("  bank down:   " + underTest.placeOrder("ORD-10", AMOUNT, GOOD));
        System.out.println("  gateway saw " + fake.callCount() + " calls, keys " + fake.seenKeys());
        System.out.println();
        System.out.println("  Three payment outcomes exercised in microseconds, with no");
        System.out.println("  sandbox account and nothing to clean up. That is what the");
        System.out.println("  interface bought you, and it is worth saying out loud.");
    }
}
