import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The classic version of the pattern, and the one most interviewers reach for
 * by default.
 *
 * One concrete class, not a Rs2000Handler / Rs500Handler hierarchy — the
 * denomination is data, not behaviour, exactly as in lld/00-modelling. Being
 * consistent about that across two very different problems is the sort of thing
 * that reads as taste rather than recall.
 *
 * The important part is plan-then-commit. A naive chain hands out notes as it
 * goes, and when the last handler discovers it cannot make the remaining Rs 50,
 * the customer has already been given Rs 4,950 of a Rs 5,000 withdrawal. So the
 * chain computes a full plan first, and only the dispenser commits it — and
 * only if the remainder came out at zero.
 */
public class DenominationHandler {

    private final int denomination;
    private int available;
    private DenominationHandler next;

    public DenominationHandler(int denomination, int available) {
        this.denomination = denomination;
        this.available = available;
    }

    public DenominationHandler then(DenominationHandler next) {
        this.next = next;
        return next;
    }

    /** Returns what it could not cover. Mutates nothing. */
    public int plan(int remaining, Map<Integer, Integer> into) {
        int wanted = remaining / denomination;
        int give = Math.min(wanted, available);
        if (give > 0) {
            into.put(denomination, give);
            remaining -= give * denomination;
        }
        return next == null ? remaining : next.plan(remaining, into);
    }

    void commit(Map<Integer, Integer> plan) {
        Integer taken = plan.get(denomination);
        if (taken != null) {
            available -= taken;
        }
        if (next != null) {
            next.commit(plan);
        }
    }

    Map<Integer, Integer> remainingNotes() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        counts.put(denomination, available);
        if (next != null) {
            counts.putAll(next.remainingNotes());
        }
        return counts;
    }
}
