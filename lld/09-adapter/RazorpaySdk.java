import java.util.HashMap;
import java.util.Map;

/**
 * Stand-in for a third-party SDK. Pretend you cannot change a line of it.
 *
 * Note the shape: nested builders, amounts in paise as an int, a bag of string
 * options, and errors thrown as an exception carrying a vendor-specific code.
 * Every one of those is a decision somebody else made, and the adapter's job is
 * to make sure none of them reach your OrderService.
 */
public class RazorpaySdk {

    public static class RzpException extends RuntimeException {
        public final String code;
        public RzpException(String code, String description) {
            super(description);
            this.code = code;
        }
    }

    public static class RzpOrder {
        public String id;
        public String status;      // "captured", "failed"
        public int amount;         // paise
    }

    private final String keyId;
    private boolean up = true;

    public RazorpaySdk(String keyId) { this.keyId = keyId; }

    public void goDown() { up = false; }
    public void comeBack() { up = true; }

    public RzpOrder createOrder(Map<String, Object> options) {
        if (!up) {
            throw new RzpException("SERVER_ERROR", "gateway unreachable");
        }
        String token = String.valueOf(options.get("method_token"));
        if (token.contains("declined")) {
            throw new RzpException("BAD_REQUEST_ERROR", "card declined by issuer");
        }
        if (token.contains("weird")) {
            throw new RzpException("SOME_NEW_CODE_2027", "something we have never seen");
        }

        RzpOrder order = new RzpOrder();
        order.id = "pay_rzp_" + options.get("receipt");
        order.status = "captured";
        order.amount = (Integer) options.get("amount");
        return order;
    }

    /** Their status endpoint. Real SDKs have one; use it rather than a test charge. */
    public boolean ping() { return up; }

    public Map<String, Object> newOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("key_id", keyId);
        return options;
    }
}
