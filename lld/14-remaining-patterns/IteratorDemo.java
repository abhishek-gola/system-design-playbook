import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Iterator: traverse without exposing the internals.
 *
 * Java's Iterable IS this pattern, so an interview question here is always
 * about writing a custom one — usually over a tree, or over a paginated API
 * where hasNext() quietly fetches the next page.
 *
 * The tree below flattens depth-first, and the caller writes a plain for-each
 * over a structure that is not a list.
 */
public class IteratorDemo {

    static class TreeNode implements Iterable<String> {
        final String value;
        final java.util.List<TreeNode> children = new java.util.ArrayList<>();

        TreeNode(String value) { this.value = value; }

        TreeNode add(TreeNode child) { children.add(child); return this; }

        @Override
        public Iterator<String> iterator() {
            return new Iterator<>() {
                private final Deque<TreeNode> stack = new ArrayDeque<>(java.util.List.of(TreeNode.this));

                @Override
                public boolean hasNext() { return !stack.isEmpty(); }

                @Override
                public String next() {
                    TreeNode node = stack.pop();
                    // Push in reverse so children come out left to right.
                    for (int i = node.children.size() - 1; i >= 0; i--) {
                        stack.push(node.children.get(i));
                    }
                    return node.value;
                }
            };
        }
    }

    public static void show() {
        TreeNode tree = new TreeNode("root")
                .add(new TreeNode("a").add(new TreeNode("a1")).add(new TreeNode("a2")))
                .add(new TreeNode("b"));

        StringBuilder order = new StringBuilder();
        for (String value : tree) {
            order.append(value).append(' ');
        }
        System.out.println("    depth-first: " + order.toString().trim());
        System.out.println("    The caller wrote a for-each over something that is not a list");
        System.out.println("    and never saw the stack.");
    }
}
