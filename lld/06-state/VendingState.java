/**
 * Every event the machine can receive, once each.
 *
 * Each implementation handles the transitions that are legal from where it
 * stands and refuses the rest. That refusal is the point: an illegal transition
 * stops being something you remember to guard against and becomes something the
 * design says out loud.
 */
public interface VendingState {

    String name();

    void insertCoin(Machine machine, Coin coin);

    void selectItem(Machine machine, String code);

    void dispense(Machine machine);

    void refund(Machine machine);

    /** Shared refusal, so each state only writes the transitions it allows. */
    default void reject(String action, String why) {
        throw new IllegalStateException(action + " not allowed in " + name() + ": " + why);
    }

    /**
     * Selection is legal from two states — HAS_MONEY and OUT_OF_STOCK (where
     * you're picking something else instead). Rather than copy it, both delegate
     * here.
     *
     * Shared transition logic living on the interface is a small thing worth
     * pointing at in an interview: it shows you noticed the duplication rather
     * than pasting it twice, and it keeps each state class down to the
     * transitions it actually owns.
     */
    default void attemptSelect(Machine machine, String code) {
        if (!machine.isKnown(code)) {
            throw new IllegalArgumentException("no such item: " + code);
        }
        if (!machine.inStock(code)) {
            machine.select(code);
            machine.setState(new OutOfStockState());
            return;
        }
        int price = machine.priceOf(code);
        if (machine.balance() < price) {
            reject("selectItem", machine.nameOf(code) + " costs Rs " + price
                    + ", balance is Rs " + machine.balance());
        }
        machine.select(code);
        machine.setState(new DispensingState());
    }
}
