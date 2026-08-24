/**
 * The whole pattern in one class.
 *
 * `captured` is recorded during execute() and used during undo(). That is the
 * design rule worth stating out loud: a command must capture, at execute time,
 * whatever the world is about to forget. Without it, undo() would have to look
 * at the current board to guess what used to be on the target square, and it
 * would be wrong the moment two commands touch the same square.
 */
public class MoveCommand implements Command {

    private final Board board;
    private final Square from;
    private final Square to;

    private Piece moved;
    private Piece captured;
    private boolean executed;

    public MoveCommand(Board board, Square from, Square to) {
        this.board = board;
        this.from = from;
        this.to = to;
    }

    @Override
    public void execute() {
        moved = board.pieceAt(from);
        if (moved == null) {
            throw new IllegalStateException("no piece on " + from);
        }
        captured = board.pieceAt(to);        // may be null, and null is fine
        board.move(from, to);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) {
            throw new IllegalStateException("cannot undo a command that never ran");
        }
        board.move(to, from);
        board.place(to, captured);           // null clears the square again
        executed = false;
    }

    @Override
    public String describe() {
        return moved + " " + from + "-" + to + (captured != null ? " x" + captured : "");
    }

    /** The serialised form: enough to replay this move onto a fresh board. */
    public String toLogLine() {
        return "MOVE " + from + " " + to;
    }

    public static MoveCommand fromLogLine(Board board, String line) {
        String[] parts = line.split(" ");
        return new MoveCommand(board, new Square(parts[1]), new Square(parts[2]));
    }
}
