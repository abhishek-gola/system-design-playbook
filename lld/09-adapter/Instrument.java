public class Instrument {
    public enum Kind { CARD, UPI, NETBANKING }

    private final Kind kind;
    private final String token;

    public Instrument(Kind kind, String token) {
        this.kind = kind;
        this.token = token;
    }

    public Kind kind()   { return kind; }
    public String token(){ return token; }

    @Override
    public String toString() { return kind + ":" + token; }
}
