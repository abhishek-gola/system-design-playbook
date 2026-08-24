public enum Coin {
    ONE(1), TWO(2), FIVE(5), TEN(10);

    private final int rupees;

    Coin(int rupees) { this.rupees = rupees; }

    public int rupees() { return rupees; }
}
