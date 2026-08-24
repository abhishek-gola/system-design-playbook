import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Memento: snapshot and restore without exposing the internals.
 *
 * The caretaker (the editor's history) holds mementos it cannot read. Only the
 * originator knows how to make one and how to restore from it. That opacity is
 * the pattern — a "snapshot" the caretaker can inspect and edit is just a
 * public field with extra steps.
 *
 * Use this instead of Command-based undo when the action is not cleanly
 * reversible. Applying a filter to an image has no inverse; you keep the pixels
 * you had before. The cost is memory proportional to what you snapshot, so
 * snapshot the smallest thing that works.
 */
public class MementoDemo {

    /** Opaque on purpose: no getters, and the field is private. */
    public static final class Snapshot {
        private final String state;
        private Snapshot(String state) { this.state = state; }
    }

    static class Editor {
        private String text = "";

        void type(String more) { text += more; }

        void applyIrreversibleFilter() {
            text = text.toUpperCase().replace(" ", "");   // no inverse exists
        }

        Snapshot save()                 { return new Snapshot(text); }
        void restore(Snapshot snapshot) { this.text = snapshot.state; }
        String text()                   { return text; }
    }

    public static void show() {
        Editor editor = new Editor();
        Deque<Snapshot> history = new ArrayDeque<>();

        editor.type("design the ");
        history.push(editor.save());
        editor.type("rate limiter");
        history.push(editor.save());

        System.out.println("    before filter: '" + editor.text() + "'");
        editor.applyIrreversibleFilter();
        System.out.println("    after filter:  '" + editor.text() + "'");
        System.out.println("    There is no undo() that could reverse that, so we restore:");

        editor.restore(history.pop());
        System.out.println("    restored:      '" + editor.text() + "'");
        editor.restore(history.pop());
        System.out.println("    restored:      '" + editor.text() + "'");
        System.out.println("    The history held two snapshots and could not read either one.");
    }
}
