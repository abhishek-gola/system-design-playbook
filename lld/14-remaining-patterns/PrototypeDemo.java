import java.util.ArrayList;
import java.util.List;

/**
 * Prototype: copy an existing object rather than building one, when
 * construction is expensive.
 *
 * The Java-specific note worth having ready: Cloneable is broken by design —
 * it is a marker interface with no clone() method on it, Object.clone() is
 * protected, and the default is a shallow copy that will quietly share your
 * mutable fields. A copy constructor is what you would actually write, and
 * saying so is a better answer than reciting the pattern.
 *
 * The demo shows the shallow-copy bug and then the fix.
 */
public class PrototypeDemo {

    static class RuleSet {
        final String name;
        final List<String> rules;

        RuleSet(String name, List<String> rules) {
            this.name = name;
            this.rules = rules;
            expensiveSetup();
        }

        /** Copy constructor: deep where it matters. */
        RuleSet(RuleSet other, String newName) {
            this.name = newName;
            this.rules = new ArrayList<>(other.rules);     // the fix
            // no expensiveSetup() — that is the entire reason to copy
        }

        RuleSet shallowCopyWithBug(String newName) {
            RuleSet copy = new RuleSet(newName, this.rules); // shares the list
            return copy;
        }

        private void expensiveSetup() {
            for (int i = 0; i < 50_000; i++) {
                Math.sqrt(i);                                // stand-in for real work
            }
        }
    }

    public static void show() {
        RuleSet template = new RuleSet("baseline", new ArrayList<>(List.of("velocity", "blacklist")));

        RuleSet shared = template.shallowCopyWithBug("shared");
        shared.rules.add("ml-score");
        System.out.println("    shallow copy, then edited the copy:");
        System.out.println("      copy:     " + shared.rules);
        System.out.println("      template: " + template.rules + "   <- changed too");

        RuleSet template2 = new RuleSet("baseline", new ArrayList<>(List.of("velocity", "blacklist")));
        RuleSet safe = new RuleSet(template2, "safe");
        safe.rules.add("ml-score");
        System.out.println("    copy constructor with a new list:");
        System.out.println("      copy:     " + safe.rules);
        System.out.println("      template: " + template2.rules + "   <- untouched");
        System.out.println("    Prefer a copy constructor. Cloneable is a trap.");
    }
}
