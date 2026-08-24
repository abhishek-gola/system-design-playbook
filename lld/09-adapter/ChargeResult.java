/**
 * The domain type the rest of the codebase sees. No provider ever appears in
 * this file, and that is the test of whether the adapter did its job.
 */
public final class ChargeResult {

    public enum Status {
        CAPTURED,
        DECLINED,
        PROVIDER_ERROR,
        /** A vendor code we have not mapped. Better than leaking the raw one. */
        UNKNOWN
    }

    private final Status status;
    private final String paymentId;
    private final String reason;
    private final String provider;

    private ChargeResult(Status status, String paymentId, String reason, String provider) {
        this.status = status;
        this.paymentId = paymentId;
        this.reason = reason;
        this.provider = provider;
    }

    public static ChargeResult captured(String paymentId, String provider) {
        return new ChargeResult(Status.CAPTURED, paymentId, null, provider);
    }

    public static ChargeResult declined(String reason, String provider) {
        return new ChargeResult(Status.DECLINED, null, reason, provider);
    }

    public static ChargeResult providerError(String reason, String provider) {
        return new ChargeResult(Status.PROVIDER_ERROR, null, reason, provider);
    }

    public static ChargeResult unknown(String vendorCode, String provider) {
        return new ChargeResult(Status.UNKNOWN, null, "unmapped vendor code " + vendorCode, provider);
    }

    public Status status()      { return status; }
    public String paymentId()   { return paymentId; }
    public String reason()      { return reason; }
    public String provider()    { return provider; }
    public boolean isSuccess()  { return status == Status.CAPTURED; }

    /** A caller can retry the next provider on this, but never on a decline. */
    public boolean isRetryableElsewhere() {
        return status == Status.PROVIDER_ERROR;
    }

    @Override
    public String toString() {
        return status + (paymentId != null ? " " + paymentId : "")
                + (reason != null ? " (" + reason + ")" : "")
                + " via " + provider;
    }
}
