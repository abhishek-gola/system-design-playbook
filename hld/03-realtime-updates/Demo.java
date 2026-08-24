import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-time updates, run as a simulation.
 *
 * There is no socket in this folder and that is on purpose. Opening a WebSocket
 * is a library call. Knowing which of your two hundred servers is holding the
 * socket for the person being messaged is the design, and it is what the
 * interview is actually about.
 *
 * Everything here is single-threaded and driven by a hand-cranked clock, so the
 * output is identical on every machine and the thirty-second TTL expires in the
 * time it takes to print a line.
 */
public class Demo {

    private static final long REGISTRY_TTL_MILLIS = 30_000L;

    public static void main(String[] args) {
        ManualTicker clock = new ManualTicker(0L);
        ConnectionRegistry registry = new ConnectionRegistry(clock, REGISTRY_TTL_MILLIS);
        Cluster cluster = new Cluster(clock, registry);

        ChatServer s1 = cluster.addServer("chat-1");
        ChatServer s2 = cluster.addServer("chat-2");
        ChatServer s3 = cluster.addServer("chat-3");

        String convo = "alice:bob";

        section("1. Routing a message between two servers");
        System.out.println("  A load balancer put alice on one server and bob on another.");
        System.out.println("  Neither of them chose it and neither of them knows about it.");
        s1.connect("alice");
        s2.connect("bob");
        clock.advance(1_000);
        s1.accept(Message.draft("m-001", convo, "alice", "bob", "are you coming?"));

        section("2. The recipient is offline");
        System.out.println("  carol has no socket anywhere in the fleet. Nothing is lost —");
        System.out.println("  the message is already durable, it just cannot be pushed.");
        clock.advance(1_000);
        s1.accept(Message.draft("m-002", "alice:carol", "alice", "carol", "dinner friday?"));

        section("3. Ordering, decided by sequence numbers rather than clocks");
        System.out.println("  Two of these arrive on chat-1 and one on chat-2. The counter");
        System.out.println("  belongs to the conversation, so the order is the same for both");
        System.out.println("  participants no matter whose server accepted what.");
        clock.advance(1_000);
        s1.accept(Message.draft("m-003", convo, "alice", "bob", "the table is booked for 8"));
        clock.advance(1_000);
        s2.accept(Message.draft("m-004", convo, "bob", "alice", "on my way"));
        clock.advance(1_000);
        s1.accept(Message.draft("m-005", convo, "alice", "bob", "no rush"));

        section("4. A retry that must not turn into two messages");
        System.out.println("  bob's reply timed out on the client side, so the phone sends it");
        System.out.println("  again with the id it generated the first time.");
        clock.advance(1_000);
        s2.accept(Message.draft("m-004", convo, "bob", "alice", "on my way"));

        section("5. A server crashes, and the registry does not know yet");
        s2.crash();
        clock.advance(1_000);
        s1.accept(Message.draft("m-006", convo, "alice", "bob", "still there?"));
        System.out.println("  That message is safe. It is in bob's inbox behind an undelivered");
        System.out.println("  cursor, and bob's phone gets a push notification the normal way.");

        section("6. The TTL cleans up after the crashed server");
        System.out.println("  chat-1 and chat-3 keep refreshing the sockets they hold. chat-2 is");
        System.out.println("  a dead process and refreshes nothing, so only its entries rot.");
        clock.advance(REGISTRY_TTL_MILLIS + 1_000);
        s1.heartbeat();
        s3.heartbeat();
        List<String> expired = registry.sweepExpired();
        System.out.println("    sweep at t=" + clock.nowMillis() + "ms removed: " + expired);
        System.out.println("    registry entries remaining: " + registry.size());

        section("7. bob reconnects, to a different server");
        System.out.println("  He does not tell the server where he left off. The server reads the");
        System.out.println("  cursor and replays the backlog in sequence order.");
        clock.advance(1_000);
        s3.connect("bob");

        section("8. The other answer: consistent hashing instead of a lookup");
        consistentHashing();

        section("Report");
        cluster.report();
        System.out.println();
        System.out.println("  Pushes per server: chat-1=" + s1.pushed()
                + " chat-2=" + s2.pushed() + " chat-3=" + s3.pushed());
        System.out.println("  chat-2 crashed holding one socket. Nothing addressed to bob was");
        System.out.println("  lost, because delivery was never the thing that made it durable.");
    }

    /**
     * The contrast. With a ring, any server works out the owner of a user id
     * locally — no Redis round trip on the hot path of every single message.
     * The catch is in ConsistentHashRing's comment and it is the sentence that
     * matters: the ring says where the connection should be, not where it is.
     */
    private static void consistentHashing() {
        ConsistentHashRing ring = new ConsistentHashRing();
        ring.addNode("chat-1");
        ring.addNode("chat-2");
        ring.addNode("chat-3");

        System.out.println("  Every server holds the same ring, so all three agree with no lookup:");
        for (String user : List.of("alice", "bob", "carol", "dave")) {
            System.out.println("    ownerOf(" + user + ") = " + ring.ownerOf(user));
        }

        List<String> users = new ArrayList<>();
        for (int i = 0; i < 3_000; i++) {
            users.add("user-" + i);
        }
        System.out.println("  3000 users across three nodes: " + ring.distribute(users));

        Map<String, String> before = new HashMap<>();
        for (String user : users) {
            before.put(user, ring.ownerOf(user));
        }
        ring.removeNode("chat-2");
        int moved = 0;
        for (String user : users) {
            if (!ring.ownerOf(user).equals(before.get(user))) {
                moved++;
            }
        }
        System.out.println("  chat-2 removed: " + ring.distribute(users));
        System.out.println("  users whose owner changed: " + moved + " of 3000 ("
                + (moved * 100 / 3_000) + "%) — only the dead node's share reshuffles,");
        System.out.println("  which is the whole point of the ring over a plain modulo.");
        System.out.println("  But note what just happened: those users' phones are still");
        System.out.println("  connected wherever they were. The ring changed its mind about");
        System.out.println("  where they live and nobody told the sockets. That gap is why chat");
        System.out.println("  usually keeps the registry and pays for the lookup.");
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
