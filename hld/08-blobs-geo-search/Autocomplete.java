import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Autocomplete is not search, and treating it as search is the mistake.
 *
 * Search has a latency budget of a few hundred milliseconds and runs once per
 * query. Autocomplete runs on every keystroke, so it has a single-digit
 * millisecond budget and roughly ten times the query volume of the search it
 * feeds. Nothing on that path may touch a database.
 *
 * So it is a trie held in memory, with the top K completions precomputed at
 * every node. A lookup walks one node per character and returns a list that was
 * computed hours ago. It is O(length of the prefix) and completely independent
 * of how many queries exist, which is the property that makes the whole thing
 * work.
 *
 * The precomputation is what turns a data structure into a system:
 *
 *  - The weights come from a query log, aggregated offline. Suggestions rank by
 *    what people actually searched for, which is also why autocomplete has a
 *    feedback loop — suggesting something makes it more popular, which makes it
 *    rank higher.
 *  - The whole trie is rebuilt offline and swapped in, rather than updated in
 *    place. Rebuilding is a batch job that can take minutes, and suggestions
 *    being a few hours stale is invisible to users.
 *  - The exception is anything trending, which is why real systems layer a
 *    small, frequently rebuilt trie of recent queries over the big stable one.
 *
 * Memory is the real constraint, and the honest answer to "how do you shard
 * it": by prefix. Every node under "lo" lives on the same machine, and a
 * request routes on its first two or three characters.
 */
public final class Autocomplete {

    public record Suggestion(String query, long weight) {
    }

    private static final class Node {
        // TreeMap rather than HashMap purely so the demo prints in a stable
        // order. A real one uses an array of 26 or a compressed radix tree,
        // because a HashMap per node is a lot of object overhead at scale.
        final Map<Character, Node> children = new TreeMap<>();
        final List<Suggestion> top = new ArrayList<>();
    }

    private final int k;
    private final Node root = new Node();
    private final List<Suggestion> queryLog = new ArrayList<>();

    private long lastLookupSteps = 0;
    private long lastScanComparisons = 0;

    public Autocomplete(int k) {
        this.k = k;
    }

    public void addQueryLogEntry(String query, long weight) {
        queryLog.add(new Suggestion(query, weight));
    }

    /** The offline job. Every node on a query's path gets that query offered to its top-K list. */
    public void build() {
        for (Suggestion suggestion : queryLog) {
            Node node = root;
            offer(node, suggestion);
            for (int i = 0; i < suggestion.query().length(); i++) {
                node = node.children.computeIfAbsent(suggestion.query().charAt(i), c -> new Node());
                offer(node, suggestion);
            }
        }
    }

    private void offer(Node node, Suggestion suggestion) {
        node.top.add(suggestion);
        node.top.sort((a, b) -> {
            int byWeight = Long.compare(b.weight(), a.weight());
            return byWeight != 0 ? byWeight : a.query().compareTo(b.query());
        });
        while (node.top.size() > k) {
            node.top.remove(node.top.size() - 1);
        }
    }

    /** The request path. One node per character, then return a list somebody else already sorted. */
    public List<Suggestion> suggest(String prefix) {
        lastLookupSteps = 0;
        Node node = root;
        for (int i = 0; i < prefix.length(); i++) {
            lastLookupSteps++;
            node = node.children.get(prefix.charAt(i));
            if (node == null) {
                return new ArrayList<>();
            }
        }
        return node.top;
    }

    /** What it costs without the trie: touch every known query, every keystroke. */
    public List<Suggestion> scanWholeLog(String prefix) {
        lastScanComparisons = 0;
        List<Suggestion> hits = new ArrayList<>();
        for (Suggestion suggestion : queryLog) {
            lastScanComparisons++;
            if (suggestion.query().startsWith(prefix)) {
                hits.add(suggestion);
            }
        }
        hits.sort((a, b) -> {
            int byWeight = Long.compare(b.weight(), a.weight());
            return byWeight != 0 ? byWeight : a.query().compareTo(b.query());
        });
        while (hits.size() > k) {
            hits.remove(hits.size() - 1);
        }
        return hits;
    }

    public long lastLookupSteps() {
        return lastLookupSteps;
    }

    public long lastScanComparisons() {
        return lastScanComparisons;
    }

    public int queryLogSize() {
        return queryLog.size();
    }

    public int nodeCount() {
        return count(root);
    }

    private static int count(Node node) {
        int total = 1;
        for (Node child : node.children.values()) {
            total += count(child);
        }
        return total;
    }
}
