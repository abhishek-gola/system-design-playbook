public class HasMoneyState implements VendingState {

    @Override
    public String name() { return "HAS_MONEY"; }

    /** More coins are fine, and the state doesn't change. */
    @Override
    public void insertCoin(Machine machine, Coin coin) {
        machine.addBalance(coin);
    }

    @Override
    public void selectItem(Machine machine, String code) {
        attemptSelect(machine, code);
    }

    @Override
    public void dispense(Machine machine) {
        reject("dispense", "select an item first");
    }

    /**
     * The transition candidates forget. "What happens if they change their mind
     * after putting money in" is a requirements question, and asking it
     * unprompted in minute three is worth more than the code that answers it.
     */
    @Override
    public void refund(Machine machine) {
        machine.refundBalance();
        machine.setState(new IdleState());
    }
}
