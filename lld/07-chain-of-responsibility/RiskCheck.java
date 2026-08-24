/**
 * handle() is final, evaluate() is abstract.
 *
 * That is a template method sitting inside the chain: the traversal is fixed,
 * only the decision varies. It stops a subclass from forgetting to call next(),
 * which is the single most common bug in a hand-rolled chain and one you should
 * mention while you write the `final` keyword.
 */
public abstract class RiskCheck {

    private RiskCheck next;

    /** Returns the argument so the chain reads as a sentence when you build it. */
    public RiskCheck then(RiskCheck next) {
        this.next = next;
        return next;
    }

    public final Decision handle(Txn txn) {
        Decision decision;
        try {
            decision = evaluate(txn);
        } catch (RuntimeException e) {
            // A check that cannot reach its dependency has to fail somewhere,
            // and WHICH way is a per-check decision, not a global setting.
            decision = onDependencyFailure(e);
        }
        decision.record(rule(), decision.reason());

        if (decision.isTerminal()) {
            return decision;
        }
        if (next == null) {
            return Decision.allow("passed every check").inheritTrail(decision);
        }
        return next.handle(txn).inheritTrail(decision);
    }

    public abstract String rule();

    protected abstract Decision evaluate(Txn txn);

    /**
     * Default is fail-closed. Cheap local checks should keep it; the expensive
     * network ones override it, because a 200ms budget does not allow for
     * waiting on a scorer that is having a bad day.
     */
    protected Decision onDependencyFailure(RuntimeException e) {
        return Decision.block("dependency unavailable: " + e.getMessage());
    }
}
