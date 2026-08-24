public class Demo {

    public static void main(String[] args) {
        Machine machine = new Machine()
                .stockItem("A1", "Lays",       20, 2)
                .stockItem("A2", "Dairy Milk", 40, 1)
                .stockItem("B1", "Coke",       35, 0);   // sold out from the start

        machine.printInventory();

        System.out.println();
        System.out.println("== Happy path ==");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        System.out.println("      balance Rs " + machine.balance());
        machine.selectItem("A1");
        machine.dispense();
        machine.printInventory();

        System.out.println();
        System.out.println("== Every illegal transition, refused by the state that owns it ==");
        illegal("selectItem while IDLE",  () -> machine.selectItem("A1"));
        illegal("dispense while IDLE",    () -> machine.dispense());
        illegal("refund while IDLE",      () -> machine.refund());

        machine.insertCoin(Coin.TEN);
        illegal("dispense while HAS_MONEY",     () -> machine.dispense());
        illegal("select something unaffordable", () -> machine.selectItem("A2"));

        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        System.out.println("      balance Rs " + machine.balance());
        machine.selectItem("A2");
        illegal("insertCoin while DISPENSING",  () -> machine.insertCoin(Coin.ONE));
        illegal("refund while DISPENSING",      () -> machine.refund());
        machine.dispense();

        System.out.println();
        System.out.println("== The refund path, which is the transition people forget ==");
        machine.insertCoin(Coin.FIVE);
        machine.insertCoin(Coin.FIVE);
        System.out.println("      balance Rs " + machine.balance() + ", changed their mind");
        machine.refund();
        System.out.println("      state is now " + machine.stateName());

        System.out.println();
        System.out.println("== Sold out, and picking something else instead ==");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        machine.selectItem("B1");
        System.out.println("      state is now " + machine.stateName());
        illegal("dispense a sold-out item", () -> machine.dispense());
        System.out.println("      picking A1 instead:");
        machine.selectItem("A1");
        machine.dispense();
        machine.printInventory();

        System.out.println();
        System.out.println("== And now A1 is gone too ==");
        machine.insertCoin(Coin.TEN);
        machine.insertCoin(Coin.TEN);
        machine.selectItem("A1");
        System.out.println("      state is now " + machine.stateName() + ", taking the refund");
        machine.refund();
        machine.printInventory();

        System.out.println();
        System.out.println("  Count the `if` statements in Machine: zero. Every branch above");
        System.out.println("  lives in the one state that owns it, and adding a MaintenanceState");
        System.out.println("  is a new file that no existing class has to hear about.");
    }

    private static void illegal(String label, Runnable attempt) {
        try {
            attempt.run();
            System.out.println("    " + label + ": ALLOWED — that's a hole in the state machine");
        } catch (RuntimeException e) {
            System.out.println("    " + label + ": " + e.getMessage());
        }
    }
}
