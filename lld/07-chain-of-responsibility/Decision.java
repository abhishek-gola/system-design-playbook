import java.util.ArrayList;
import java.util.List;

/**
 * Four outcomes, not two.
 *
 * A check that returns boolean is the version that does not survive contact
 * with production. REVIEW is the one worth adding unprompted: real risk systems
 * do allow / review / block, because a false positive on a genuine customer
 * costs far more than a human glance does.
 *
 * The trail matters as much as the verdict. Six weeks after a false positive
 * somebody will ask why a transaction was blocked, and "rule velocity-per-user
 * fired at 15:04" is the difference between a two-minute answer and an
 * afternoon reading logs.
 */
public class Decision {

    public enum Outcome {
        CONTINUE,   // nothing to say, pass it on
        REVIEW,     // suspicious — keep going, but remember
        ALLOW,      // explicitly cleared, skip the rest of the chain
        BLOCK       // stop
    }

    private final Outcome outcome;
    private final String reason;
    private final List<String> trail = new ArrayList<>();
    private boolean flaggedForReview;

    private Decision(Outcome outcome, String reason) {
        this.outcome = outcome;
        this.reason = reason;
    }

    public static Decision cont()                { return new Decision(Outcome.CONTINUE, null); }
    public static Decision review(String reason) { return new Decision(Outcome.REVIEW, reason); }
    public static Decision allow(String reason)  { return new Decision(Outcome.ALLOW, reason); }
    public static Decision block(String reason)  { return new Decision(Outcome.BLOCK, reason); }

    /** ALLOW and BLOCK stop the chain. CONTINUE and REVIEW do not. */
    public boolean isTerminal() {
        return outcome == Outcome.ALLOW || outcome == Outcome.BLOCK;
    }

    public Outcome outcome()   { return outcome; }
    public String reason()     { return reason; }
    public List<String> trail(){ return trail; }
    public boolean needsReview() { return flaggedForReview || outcome == Outcome.REVIEW; }

    Decision record(String rule, String note) {
        trail.add(rule + (note == null ? "" : ": " + note));
        return this;
    }

    Decision inheritTrail(Decision earlier) {
        trail.addAll(0, earlier.trail);
        this.flaggedForReview |= earlier.needsReview();
        return this;
    }

    @Override
    public String toString() {
        String verdict = needsReview() && outcome != Outcome.BLOCK
                ? outcome + "+REVIEW" : outcome.toString();
        return verdict + (reason == null ? "" : " (" + reason + ")") + "  trail=" + trail;
    }
}
