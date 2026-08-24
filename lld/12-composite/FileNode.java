import java.util.List;
import java.util.function.Predicate;

/**
 * The leaf. Called FileNode rather than File only to stay clear of java.io.File
 * — in an interview, calling it File is fine and nobody will mind.
 */
public class FileNode implements Node {

    private final String name;
    private final long bytes;

    public FileNode(String name, long bytes) {
        this.name = name;
        this.bytes = bytes;
    }

    @Override
    public String name() { return name; }

    @Override
    public long sizeBytes() { return bytes; }

    @Override
    public List<Node> find(Predicate<Node> matches) {
        return matches.test(this) ? List.of(this) : List.of();
    }

    @Override
    public String render(int depth) {
        return indent(depth) + name + "  " + humanBytes(bytes) + "\n";
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "K";
        return (bytes / (1024 * 1024)) + "M";
    }
}
