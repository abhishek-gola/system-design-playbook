public class OrderItem {
    private final String name;
    private final long unitPricePaise;
    private final int quantity;

    public OrderItem(String name, long unitPricePaise, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.name = name;
        this.unitPricePaise = unitPricePaise;
        this.quantity = quantity;
    }

    public String name()      { return name; }
    public int quantity()     { return quantity; }
    public long totalPaise()  { return unitPricePaise * quantity; }

    @Override
    public String toString() {
        return quantity + "x " + name + " (" + rupees(totalPaise()) + ")";
    }

    static String rupees(long paise) {
        return "Rs " + (paise / 100) + "." + String.format("%02d", paise % 100);
    }
}
