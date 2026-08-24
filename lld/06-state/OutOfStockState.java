public class OutOfStockState implements VendingState {

    @Override
    public String name() { return "OUT_OF_STOCK"; }

    @Override
    public void insertCoin(Machine machine, Coin coin) {
        reject("insertCoin", "pick something else or take a refund first");
    }

    /** Picking a different item is the whole reason this isn't a dead end. */
    @Override
    public void selectItem(Machine machine, String code) {
        attemptSelect(machine, code);
    }

    @Override
    public void dispense(Machine machine) {
        reject("dispense", machine.nameOf(machine.selected()) + " is sold out");
    }

    @Override
    public void refund(Machine machine) {
        machine.refundBalance();
        machine.setState(new IdleState());
    }
}
