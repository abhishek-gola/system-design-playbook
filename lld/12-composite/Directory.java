import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The composite. Every method here is the same shape: do the local part, then
 * ask the children to do theirs.
 *
 * The recursion lives in this class and nowhere else, which is exactly what
 * keeps the client flat.
 */
public class Directory implements Node {

    private final String name;
    private final List<Node> children = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    /** Child management lives here, not on Node. That is the trade-off in one line. */
    public Directory add(Node child) {
        children.add(child);
        return this;
    }

    public boolean remove(String childName) {
        return children.removeIf(child -> child.name().equals(childName));
    }

    public List<Node> children() {
        return List.copyOf(children);
    }

    @Override
    public String name() { return name; }

    @Override
    public long sizeBytes() {
        return children.stream().mapToLong(Node::sizeBytes).sum();
    }

    /**
     * Nothing here stops you adding a directory to itself, and this method would
     * then recurse until the stack runs out. Real file systems have hard links
     * and handle it with a visited set, which is what safeSizeBytes does.
     *
     * Worth mentioning in an interview, not worth implementing unless asked.
     */
    public long safeSizeBytes() {
        return safeSizeBytes(new HashSet<>());
    }

    private long safeSizeBytes(Set<Node> visited) {
        if (!visited.add(this)) {
            System.out.println("      cycle detected at " + name + ", not descending again");
            return 0;
        }
        long total = 0;
        for (Node child : children) {
            total += child instanceof Directory dir
                    ? dir.safeSizeBytes(visited)
                    : child.sizeBytes();
        }
        return total;
    }

    @Override
    public List<Node> find(Predicate<Node> matches) {
        List<Node> found = new ArrayList<>();
        if (matches.test(this)) {
            found.add(this);
        }
        for (Node child : children) {
            found.addAll(child.find(matches));
        }
        return found;
    }

    @Override
    public String render(int depth) {
        StringBuilder sb = new StringBuilder()
                .append(indent(depth)).append(name).append("/  ")
                .append(FileNode.humanBytes(sizeBytes())).append("\n");
        for (Node child : children) {
            sb.append(child.render(depth + 1));
        }
        return sb.toString();
    }

    public int countFiles() {
        return find(node -> node instanceof FileNode).size();
    }
}
