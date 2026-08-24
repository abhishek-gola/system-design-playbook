import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Two stacks, and one rule that people forget.
 *
 * play() clears the redo stack. Undo three moves, then make a different move,
 * and those three are no longer reachable — which is correct, because redoing
 * them would replay moves that no longer make sense on the current board.
 * Leaving the redo stack intact gives you a corrupt history, and it is the bug
 * an interviewer will go looking for.
 */
public class Game {

    private final Board board;
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final List<String> auditLog = new ArrayList<>();

    public Game(Board board) {
        this.board = board;
    }

    public void play(Command command) {
        command.execute();
        undoStack.push(command);
        redoStack.clear();                   // the branch is gone
        auditLog.add("do   " + command.describe());
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        Command command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        auditLog.add("undo " + command.describe());
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        Command command = redoStack.pop();
        command.execute();
        undoStack.push(command);
        auditLog.add("redo " + command.describe());
        return true;
    }

    public Board board()          { return board; }
    public int undoDepth()        { return undoStack.size(); }
    public int redoDepth()        { return redoStack.size(); }
    public List<String> audit()   { return List.copyOf(auditLog); }
}
