public class Piece {
    public enum Colour { WHITE, BLACK }

    private final Colour colour;
    private final String kind;

    public Piece(Colour colour, String kind) {
        this.colour = colour;
        this.kind = kind;
    }

    public Colour colour() { return colour; }
    public String kind()   { return kind; }

    @Override
    public String toString() {
        return (colour == Colour.WHITE ? "w" : "b") + kind;
    }
}
