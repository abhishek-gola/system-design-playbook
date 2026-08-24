public class DeviceFingerprintCheck extends RiskCheck {

    private final FeatureStore features;

    public DeviceFingerprintCheck(FeatureStore features) {
        this.features = features;
    }

    @Override
    public String rule() { return "device-fingerprint"; }

    @Override
    protected Decision evaluate(Txn txn) {
        if (!features.isKnownDevice(txn.userId(), txn.deviceId())) {
            // Deliberately not a block. A new phone is the single most common
            // innocent reason for this signal, and blocking on it is how you
            // teach customers to use a competitor.
            return Decision.review("first time seeing device " + txn.deviceId());
        }
        return Decision.cont();
    }
}
