/**
 * Cheap: one in-memory set lookup. Which is exactly why it goes near the front.
 */
public class BlacklistCheck extends RiskCheck {

    private final FeatureStore features;

    public BlacklistCheck(FeatureStore features) {
        this.features = features;
    }

    @Override
    public String rule() { return "card-blacklist"; }

    @Override
    protected Decision evaluate(Txn txn) {
        if (features.isBlacklisted(txn.cardFingerprint())) {
            return Decision.block("card " + txn.cardFingerprint() + " is blacklisted");
        }
        return Decision.cont();
    }

    // Keeps the inherited fail-closed default. If the blacklist store is down,
    // refusing is cheap and the customer can retry in a minute.
}
