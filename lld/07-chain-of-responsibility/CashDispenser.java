import java.util.LinkedHashMap;
import java.util.Map;

public class CashDispenser {

    private final DenominationHandler head;

    public CashDispenser(DenominationHandler head) {
        this.head = head;
    }

    public Map<Integer, Integer> withdraw(int amount) {
        if (amount <= 0 || amount % 100 != 0) {
            throw new IllegalArgumentException("amount must be a positive multiple of 100");
        }

        Map<Integer, Integer> plan = new LinkedHashMap<>();
        int shortfall = head.plan(amount, plan);

        // The case everyone forgets. Nothing has moved yet, so refusing here
        // costs nothing — whereas a chain that dispensed as it went would have
        // already handed over most of the money.
        if (shortfall > 0) {
            throw new IllegalStateException(
                    "cannot make Rs " + amount + " exactly — Rs " + shortfall
                    + " short with the notes on hand; nothing dispensed");
        }

        head.commit(plan);
        return plan;
    }

    public Map<Integer, Integer> inventory() {
        return head.remainingNotes();
    }
}
