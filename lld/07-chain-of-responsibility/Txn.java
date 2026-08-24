public class Txn {
    private final String id;
    private final String userId;
    private final String deviceId;
    private final long amountPaise;
    private final String cardFingerprint;
    private final boolean trustedMerchant;

    public Txn(String id, String userId, String deviceId, long amountPaise,
               String cardFingerprint, boolean trustedMerchant) {
        this.id = id;
        this.userId = userId;
        this.deviceId = deviceId;
        this.amountPaise = amountPaise;
        this.cardFingerprint = cardFingerprint;
        this.trustedMerchant = trustedMerchant;
    }

    public String id()              { return id; }
    public String userId()          { return userId; }
    public String deviceId()        { return deviceId; }
    public long amountPaise()       { return amountPaise; }
    public String cardFingerprint() { return cardFingerprint; }
    public boolean trustedMerchant(){ return trustedMerchant; }

    public String rupees() { return "Rs " + (amountPaise / 100); }

    @Override
    public String toString() {
        return id + " " + userId + " " + rupees() + " device=" + deviceId;
    }
}
