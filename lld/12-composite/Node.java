import java.util.List;
import java.util.function.Predicate;

/**
 * Deliberately narrow.
 *
 * There is no addChild() here, which is a choice with a real cost: the client
 * has to know it holds a Directory in order to build the tree. The gain is that
 * a leaf never advertises a method it cannot honour.
 *
 * The other school puts addChild() on Node and throws on files. Perfect
 * uniformity for the client, at the price of a Liskov violation you signed up
 * for on purpose. Swing's Component took that route.
 *
 * Neither is right. Say the trade-off out loud — interviewers know there is no
 * clean winner and are listening for whether you know it too.
 */
public interface Node {

    String name();

    long sizeBytes();

    /** Every node can be searched. A file searches itself; a directory recurses. */
    List<Node> find(Predicate<Node> matches);

    /** Rendering is the same operation at every level, which is the point. */
    String render(int depth);

    default String indent(int depth) {
        return "  ".repeat(depth + 1);
    }
}
