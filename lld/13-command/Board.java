import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sparse board: only occupied squares are in the map. Enough for the pattern,
 * and small enough that the interesting code stays visible.
 */
public class Board {

    private final Map<String, Piece> squares = new LinkedHashMap<>();

    public Board place(Square square, Piece piece) {
        if (piece == null) {
            squares.remove(square.name());
        } else {
            squares.put(square.name(), piece);
        }
        return this;
    }

    public Piece pieceAt(Square square) {
        return squares.get(square.name());
    }

    public void move(Square from, Square to) {
        Piece moving = squares.remove(from.name());
        if (moving == null) {
            throw new IllegalStateException("no piece on " + from);
        }
        squares.put(to.name(), moving);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        squares.forEach((square, piece) -> sb.append(square).append('=').append(piece).append(' '));
        return sb.toString().trim();
    }

    public int pieceCount() {
        return squares.size();
    }
}
