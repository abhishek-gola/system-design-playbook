import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deliberately dumb.
 *
 * It holds the balance, the inventory and a reference to the current state, and
 * delegates every event. There is no switch in this class, and that absence is
 * the entire gain — if Machine still decided what to do based on which state it
 * was in, you would have written the enum version with extra ceremony.
 */
public class Machine {

    private VendingState state = new IdleState();
    private int balanceRupees;
    private String selectedCode;

    private final Map<String, Integer> stock = new LinkedHashMap<>();
    private final Map<String, Integer> priceRupees = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    public Machine stockItem(String code, String name, int price, int count) {
        stock.put(code, count);
        priceRupees.put(code, price);
        names.put(code, name);
        return this;
    }

    // --- events, all delegated -----------------------------------------

    public void insertCoin(Coin coin) { state.insertCoin(this, coin); }
    public void selectItem(String code) { state.selectItem(this, code); }
    public void dispense()            { state.dispense(this); }
    public void refund()              { state.refund(this); }

    // --- what the states operate on ------------------------------------

    void setState(VendingState next) {
        System.out.println("      " + state.name() + " -> " + next.name());
        this.state = next;
    }

    public String stateName()   { return state.name(); }
    public int balance()        { return balanceRupees; }
    public String selected()    { return selectedCode; }

    void addBalance(Coin coin)  { balanceRupees += coin.rupees(); }
    void select(String code)    { selectedCode = code; }

    boolean isKnown(String code)   { return stock.containsKey(code); }
    boolean inStock(String code)   { return stock.getOrDefault(code, 0) > 0; }
    int priceOf(String code)       { return priceRupees.get(code); }
    String nameOf(String code)     { return names.get(code); }

    boolean anythingInStock() {
        return stock.values().stream().anyMatch(count -> count > 0);
    }

    int releaseItem() {
        stock.merge(selectedCode, -1, Integer::sum);
        int change = balanceRupees - priceRupees.get(selectedCode);
        System.out.println("      dispensed " + names.get(selectedCode)
                + ", change Rs " + change);
        balanceRupees = 0;
        selectedCode = null;
        return change;
    }

    int refundBalance() {
        int refunded = balanceRupees;
        balanceRupees = 0;
        selectedCode = null;
        System.out.println("      refunded Rs " + refunded);
        return refunded;
    }

    public void printInventory() {
        System.out.println("  stock: " + stock + "   state: " + state.name());
    }
}
