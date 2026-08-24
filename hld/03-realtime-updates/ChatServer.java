import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One node in the chat fleet. It owns a set of open WebSocket connections and
 * nothing else of consequence.
 *
 * The socket itself is the boring part, which is why there is no socket here.
 * What matters is that connection state is per-server, in memory, and lost when
 * the process dies. Every hard question in this folder follows from those three
 * facts.
 */
public class ChatServer {

    private final String id;
    private final Cluster cluster;

    /** userId -> open connection. One device per user here; see the README for N. */
    private final Set<String> openConnections = new LinkedHashSet<>();

    private int pushed;
    private boolean crashed;

    ChatServer(String id, Cluster cluster) {
        this.id = id;
        this.cluster = cluster;
    }

    public String id() {
        return id;
    }

    /**
     * The client opens a socket. Two things must happen and the order matters:
     * publish the route first, then drain the backlog. Do it the other way round
     * and a message that arrives during the drain finds no registry entry and
     * goes to the inbox, behind messages the device has already been shown.
     */
    public void connect(String userId) {
        openConnections.add(userId);
        cluster.registry().register(userId, id);
        System.out.println("    " + userId + " connected to " + id
                + "  (registry now says " + userId + " -> " + id + ")");
        cluster.flushInbox(userId, this);
    }

    /** A clean disconnect. The user closed the app rather than falling off a cliff. */
    public void disconnect(String userId) {
        openConnections.remove(userId);
        cluster.registry().unregister(userId);
        System.out.println("    " + userId + " disconnected cleanly from " + id
                + "  (registry entry removed immediately)");
    }

    /**
     * The unclean version. The process is gone: sockets are dead, in-memory
     * state is gone, and crucially nothing unregisters anybody. The registry
     * still points here, and will keep doing so until the TTL runs out. That gap
     * is the interesting part of the Demo.
     */
    public void crash() {
        crashed = true;
        openConnections.clear();
        System.out.println("    " + id + " CRASHED — sockets gone, and it did not "
                + "get to unregister anyone. The registry has not noticed yet.");
    }

    public boolean crashed() {
        return crashed;
    }

    /**
     * Every few seconds a live server re-registers every socket it holds, which
     * pushes the TTL out again. This is the other half of the expiry design: the
     * entry does not survive because someone remembered to delete it, it
     * survives only while somebody keeps saying "still here". A crashed server
     * says nothing, so its entries die on their own.
     */
    public void heartbeat() {
        for (String userId : openConnections) {
            cluster.registry().register(userId, id);
        }
    }

    public boolean hasConnection(String userId) {
        return openConnections.contains(userId);
    }

    /** The actual write to the socket, which in this simulation is a println. */
    public void push(Message message) {
        pushed++;
        System.out.println("      " + id + " pushes over the open socket to "
                + message.to() + ": " + message);
    }

    /** The client's send. It lands wherever the load balancer put this client. */
    public void accept(Message draft) {
        System.out.println("    " + draft.from() + " sends via " + id
                + ": \"" + draft.body() + "\" (clientMessageId=" + draft.clientMessageId() + ")");
        cluster.route(this, draft);
    }

    public int pushed() {
        return pushed;
    }
}
