public class IdleState implements VendingState {

    @Override
    public String name() { return "IDLE"; }

    @Override
    public void insertCoin(Machine machine, Coin coin) {
        machine.addBalance(coin);
        machine.setState(new HasMoneyState());
    }

    @Override
    public void selectItem(Machine machine, String code) {
        reject("selectItem", "insert a coin first");
    }

    @Override
    public void dispense(Machine machine) {
        reject("dispense", "nothing has been selected");
    }

    @Override
    public void refund(Machine machine) {
        reject("refund", "there is no balance to refund");
    }
}
