public class AmountThresholdCheck extends RiskCheck {

    private final long reviewAbovePaise;
    private final long blockAbovePaise;

    public AmountThresholdCheck(long reviewAboveRupees, long blockAboveRupees) {
        this.reviewAbovePaise = reviewAboveRupees * 100;
        this.blockAbovePaise = blockAboveRupees * 100;
    }

    @Override
    public String rule() { return "amount-threshold"; }

    @Override
    protected Decision evaluate(Txn txn) {
        if (txn.amountPaise() > blockAbovePaise) {
            return Decision.block(txn.rupees() + " is over the hard ceiling");
        }
        if (txn.amountPaise() > reviewAbovePaise) {
            return Decision.review(txn.rupees() + " is unusually large");
        }
        return Decision.cont();
    }
}
