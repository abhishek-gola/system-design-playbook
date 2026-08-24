import java.util.Map;

/**
 * The expensive one: a network call to a model server.
 *
 * Two things make this handler different from the others, and both are worth
 * saying out loud.
 *
 * It goes LAST, because ordering the chain by cost means the model only ever
 * sees traffic that survived every cheap check. On a real pipeline that is the
 * difference between scoring every transaction and scoring five percent of them.
 *
 * It fails OPEN. A 100ms budget does not allow for waiting on a scorer that is
 * having a bad day, and blocking every payment because a model server is down
 * is a self-inflicted outage. The cheap local checks fail closed; this one does
 * not. That asymmetry is a per-check decision, not a global setting.
 */
public class MlScoreCheck extends RiskCheck {

    private final Map<String, Double> scores;
    private final boolean serverDown;

    public MlScoreCheck(Map<String, Double> scores, boolean serverDown) {
        this.scores = scores;
        this.serverDown = serverDown;
    }

    @Override
    public String rule() { return "ml-score"; }

    @Override
    protected Decision evaluate(Txn txn) {
        if (serverDown) {
            throw new IllegalStateException("model server timeout after 80ms");
        }
        System.out.println("        (network call to the model server for " + txn.id() + ")");

        double score = scores.getOrDefault(txn.id(), 0.1);
        if (score >= 0.90) return Decision.block("model score " + score);
        if (score >= 0.70) return Decision.review("model score " + score);
        return Decision.cont();
    }

    @Override
    protected Decision onDependencyFailure(RuntimeException e) {
        return Decision.review("scorer unavailable, failing open: " + e.getMessage());
    }
}
