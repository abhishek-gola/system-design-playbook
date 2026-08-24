import java.util.ArrayList;
import java.util.List;

public class Demo {

    public static void main(String[] args) {
        Board board = startingPosition();
        Game game = new Game(board);
        List<String> log = new ArrayList<>();

        System.out.println("== Opening position ==");
        System.out.println("  " + board.describe());

        System.out.println();
        System.out.println("== Four moves, the last one a capture ==");
        play(game, log, board, "e2", "e4");
        play(game, log, board, "d7", "d5");
        play(game, log, board, "g1", "f3");
        play(game, log, board, "e4", "d5");          // white pawn takes black pawn
        System.out.println("  pieces on the board: " + board.pieceCount()
                + "   (one was captured)");

        System.out.println();
        System.out.println("== Undo all the way back ==");
        while (game.undo()) {
            System.out.println("  " + board.describe());
        }
        System.out.println("  pieces back to " + board.pieceCount()
                + " — the captured pawn returned, because the command kept it");

        System.out.println();
        System.out.println("== Redo twice ==");
        game.redo();
        game.redo();
        System.out.println("  " + board.describe());
        System.out.println("  undo depth " + game.undoDepth() + ", redo depth " + game.redoDepth());

        System.out.println();
        System.out.println("== The rule people forget: a new move clears the redo stack ==");
        System.out.println("  redo depth before: " + game.redoDepth());
        play(game, log, board, "b1", "c3");
        System.out.println("  redo depth after:  " + game.redoDepth());
        System.out.println("  Those futures are gone, and that is correct — redoing them would");
        System.out.println("  replay moves that no longer make sense on this board.");

        System.out.println();
        System.out.println("== The audit trail you got for free ==");
        game.audit().forEach(line -> System.out.println("  " + line));

        System.out.println();
        System.out.println("== The bridge to HLD: the same commands as a replayable log ==");
        System.out.println("  serialised:");
        log.forEach(line -> System.out.println("    " + line));

        Board replayed = startingPosition();
        for (String line : log) {
            MoveCommand.fromLogLine(replayed, line).execute();
        }
        System.out.println("  replayed onto a fresh board:");
        System.out.println("    " + replayed.describe());
        System.out.println("  Same position, rebuilt from nothing but the command history.");
        System.out.println();
        System.out.println("  That is event sourcing in eight lines: current state is a fold");
        System.out.println("  over the commands, and undo is replaying to an earlier point.");
        System.out.println("  Put those lines on a queue instead and each one is a job.");
    }

    private static void play(Game game, List<String> log, Board board, String from, String to) {
        MoveCommand move = new MoveCommand(board, new Square(from), new Square(to));
        game.play(move);
        log.add(move.toLogLine());
        System.out.println("  " + move.describe() + "   -> " + board.describe());
    }

    private static Board startingPosition() {
        return new Board()
                .place(new Square("e2"), new Piece(Piece.Colour.WHITE, "P"))
                .place(new Square("d7"), new Piece(Piece.Colour.BLACK, "P"))
                .place(new Square("g1"), new Piece(Piece.Colour.WHITE, "N"))
                .place(new Square("b1"), new Piece(Piece.Colour.WHITE, "N"))
                .place(new Square("e1"), new Piece(Piece.Colour.WHITE, "K"))
                .place(new Square("e8"), new Piece(Piece.Colour.BLACK, "K"));
    }
}
