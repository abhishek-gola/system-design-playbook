import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flyweight: share the immutable part, pass the rest in.
 *
 * The split is the whole idea, and it is what to say when asked:
 *
 *   intrinsic — shared, immutable, belongs to the type   (colour, kind, glyph)
 *   extrinsic — per use, passed in                        (which square, which pixel)
 *
 * Java's Integer.valueOf cache is a flyweight you use every day without
 * noticing. So is String interning.
 */
public class FlyweightDemo {

    /** Intrinsic state only. Immutable, so sharing is safe. */
    record PieceType(String colour, String kind) { }

    static class PieceTypeFactory {
        private final Map<String, PieceType> shared = new HashMap<>();
        int created;

        PieceType of(String colour, String kind) {
            return shared.computeIfAbsent(colour + kind, key -> {
                created++;
                return new PieceType(colour, kind);
            });
        }
    }

    /** Extrinsic state lives here, one per piece on the board. */
    record PlacedPiece(PieceType type, String square) { }

    public static void show() {
        String[] kinds = {"P", "P", "P", "P", "P", "P", "P", "P", "R", "N", "B", "Q", "K", "B", "N", "R"};

        PieceTypeFactory factory = new PieceTypeFactory();
        List<PlacedPiece> board = new ArrayList<>();
        int withoutSharing = 0;

        for (String colour : new String[]{"w", "b"}) {
            for (int i = 0; i < kinds.length; i++) {
                board.add(new PlacedPiece(factory.of(colour, kinds[i]), colour + i));
                withoutSharing++;                    // one PieceType each, naively
            }
        }

        System.out.println("    pieces on the board:        " + board.size());
        System.out.println("    PieceType objects, naive:   " + withoutSharing);
        System.out.println("    PieceType objects, shared:  " + factory.created);
        System.out.println("    Same board, " + (withoutSharing - factory.created)
                + " fewer objects, because colour and kind never change.");
        System.out.println("    Scale that to a million particles or a page of glyphs and it");
        System.out.println("    stops being a curiosity.");
    }
}
