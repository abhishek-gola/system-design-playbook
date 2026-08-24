public class DispensingState implements VendingState {

    @Override
    public String name() { return "DISPENSING"; }

    @Override
    public void insertCoin(Machine machine, Coin coin) {
        reject("insertCoin", "the machine is mid-dispense");
    }

    @Override
    public void selectItem(Machine machine, String code) {
        reject("selectItem", "already dispensing " + machine.nameOf(machine.selected()));
    }

    @Override
    public void dispense(Machine machine) {
        machine.releaseItem();
        machine.setState(new IdleState());
    }

    @Override
    public void refund(Machine machine) {
        reject("refund", "too late — the item is already on its way down");
    }
}
