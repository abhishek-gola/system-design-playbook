/**
 * The business logic. Read it and notice what is absent: no vendor type, no SDK
 * import, no error string from anybody's documentation.
 *
 * That absence is the only real test of whether the adapter did its job.
 */
public class OrderService {

    private final PaymentGateway gateway;

    public OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    public String placeOrder(String orderId, Money amount, Instrument instrument) {
        ChargeResult result = gateway.charge(amount, instrument, new IdempotencyKey(orderId));

        return switch (result.status()) {
            case CAPTURED       -> "confirmed, payment " + result.paymentId();
            case DECLINED       -> "payment declined, ask for another method";
            case PROVIDER_ERROR -> "we could not reach the bank, we will retry";
            case UNKNOWN        -> "held for manual review: " + result.reason();
        };
    }
}
