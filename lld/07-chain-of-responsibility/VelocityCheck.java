public class VelocityCheck extends RiskCheck {

    private final FeatureStore features;
    private final int reviewAbove;
    private final int blockAbove;

    public VelocityCheck(FeatureStore features, int reviewAbove, int blockAbove) {
        this.features = features;
        this.reviewAbove = reviewAbove;
        this.blockAbove = blockAbove;
    }

    @Override
    public String rule() { return "velocity-per-user"; }

    @Override
    protected Decision evaluate(Txn txn) {
        int recent = features.transactionsLastHour(txn.userId());
        if (recent > blockAbove) {
            return Decision.block(recent + " transactions in the last hour");
        }
        if (recent > reviewAbove) {
            return Decision.review(recent + " transactions in the last hour");
        }
        return Decision.cont();
    }
}
