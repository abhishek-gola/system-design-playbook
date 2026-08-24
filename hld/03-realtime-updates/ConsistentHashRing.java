import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The other answer to the routing question: don't look the user up, compute
 * where they live.
 *
 * Every server holds the same ring, so any of them can work out the owner of a
 * user id with a hash and a tree lookup — no network hop, no Redis, no cache to
 * go stale. The virtual nodes exist because a ring with one point per server
 * distributes keys badly; a hundred points per server smooths it out, and the
 * number is a tuning knob rather than a design decision.
 *
 * The reason this is a contrast rather than a replacement is worth being clear
 * about, because candidates often present consistent hashing as the better
 * answer and get pushed over. The ring tells you which server SHOULD hold the
 * connection. It does not open the connection. A phone connects through a load
 * balancer that knows nothing about the ring, so either you put a routing tier
 * in front that redirects the client to the right node, or you accept that the
 * ring's answer and reality can disagree — which is the same stale-route
 * problem you were trying to avoid, minus the TTL that fixed it.
 *
 * Where the ring genuinely wins is a system where the server, not the client,
 * decides the assignment: partitioned stream consumers, a sharded cache, a
 * scheduler handing work out. Where the registry wins is anything a client
 * dials into. For chat, I would take the registry and pay for the lookup.
 */
public class ConsistentHashRing {

    private static final int VIRTUAL_NODES = 100;

    private final SortedMap<Integer, String> ring = new TreeMap<>();
    private final List<String> nodes = new ArrayList<>();

    public void addNode(String nodeId) {
        nodes.add(nodeId);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.put(hash(nodeId + "#vn" + i), nodeId);
        }
    }

    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.remove(hash(nodeId + "#vn" + i));
        }
    }

    /** No lookup, no network call — the same input gives every server the same answer. */
    public String ownerOf(String userId) {
        if (ring.isEmpty()) {
            return null;
        }
        int h = hash(userId);
        SortedMap<Integer, String> tail = ring.tailMap(h);
        return tail.isEmpty() ? ring.get(ring.firstKey()) : tail.get(tail.firstKey());
    }

    public List<String> nodes() {
        return Collections.unmodifiableList(nodes);
    }

    /** Counts per node, sorted, so the Demo prints the same thing every time. */
    public Map<String, Integer> distribute(List<String> userIds) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String node : nodes) {
            counts.put(node, 0);
        }
        for (String userId : userIds) {
            String owner = ownerOf(userId);
            counts.merge(owner, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * FNV-1a. Deterministic, cheap, and good enough for a ring. String.hashCode
     * would also be deterministic but clusters badly on similar keys, which is
     * exactly what a set of user ids looks like.
     */
    private static int hash(String key) {
        int h = 0x811c9dc5;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x01000193;
        }
        return h;
    }
}
