import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The routing layer. This is the class the whole folder exists for.
 *
 * Everything above it is easy. A client opens a socket to some server chosen by
 * a load balancer, and neither the client nor the sender has any say in which
 * one. So when Alice's message lands on s1 and Bob's socket is held by s2, s1
 * has to find s2. That lookup, and what happens when it is wrong, is the design.
 *
 * route() below is the entire hot path, and it runs in this order for reasons:
 *
 *   1. deduplicate on the client-generated id   (a retry must not double-send)
 *   2. assign the per-conversation sequence     (ordering, decided in one place)
 *   3. append to the recipient's inbox          (durable before deliverable)
 *   4. look up the route and try to push        (best effort, may fail)
 *
 * Steps 1 to 3 are the ones that must not be skipped under load. Step 4 is
 * allowed to fail, because step 3 already means nothing is lost.
 */
public class Cluster {

    private final Ticker ticker;
    private final ConnectionRegistry registry;

    private final Map<String, ChatServer> servers = new LinkedHashMap<>();
    private final Map<String, Inbox> inboxes = new LinkedHashMap<>();

    /** conversationId -> last sequence number handed out. */
    private final Map<String, Long> sequences = new LinkedHashMap<>();

    /** clientMessageId -> the sequence we gave it the first time. The dedup table. */
    private final Map<String, Long> accepted = new LinkedHashMap<>();

    private int deliveredLive;
    private int queuedOffline;
    private int duplicatesAbsorbed;
    private int staleRoutes;
    private int pushNotifications;

    public Cluster(Ticker ticker, ConnectionRegistry registry) {
        this.ticker = ticker;
        this.registry = registry;
    }

    public ConnectionRegistry registry() {
        return registry;
    }

    public ChatServer addServer(String id) {
        ChatServer server = new ChatServer(id, this);
        servers.put(id, server);
        return server;
    }

    public void route(ChatServer origin, Message draft) {
        // (1) Idempotency. The id came from the sender's phone, so a retry after
        // a timeout carries the same one. Without this table the user who tapped
        // send twice on a bad train line shows up twice in Bob's chat, and no
        // amount of network engineering fixes that after the fact.
        Long already = accepted.get(draft.clientMessageId());
        if (already != null) {
            duplicatesAbsorbed++;
            System.out.println("      duplicate: clientMessageId=" + draft.clientMessageId()
                    + " was already accepted as seq " + already + " — dropped, nothing re-sent");
            return;
        }

        // (2) Ordering. One counter per conversation, incremented in one place.
        // Per conversation rather than global because a global counter is a
        // single point of contention for no benefit: nobody cares how your
        // messages to Bob interleave with two strangers talking in Peru.
        long seq = sequences.merge(draft.conversationId(), 1L, Long::sum);
        Message message = draft.withSeq(seq);
        accepted.put(message.clientMessageId(), seq);

        // (3) Persist before you deliver. If this process dies on the next line
        // the message still exists and the recipient gets it on reconnect.
        Inbox inbox = inboxes.computeIfAbsent(message.to(), Inbox::new);
        inbox.append(message);

        // (4) Route. The origin server has no idea where the recipient is and
        // no way to guess, so it asks. Note that the answer is only a hint — we
        // check that the server it named actually still holds the socket.
        System.out.println("      accepted as seq " + seq + "; " + origin.id()
                + " asks the registry where " + message.to() + " is");
        Optional<String> owner = registry.lookup(message.to());
        if (owner.isEmpty()) {
            queuedOffline++;
            pushNotifications++;
            System.out.println("      no registry entry for " + message.to()
                    + " — message sits in the inbox (" + inbox.undeliveredCount()
                    + " undelivered) and APNs/FCM gets a push notification instead");
            return;
        }

        ChatServer target = servers.get(owner.get());
        if (target == null || !target.hasConnection(message.to())) {
            // The registry lied. It is allowed to: it is a cache of something
            // that lives in another process's memory, and that process may have
            // died a millisecond ago. The delivery path treats a stale route
            // exactly like an offline user, which is why this case is a two-line
            // branch rather than an incident.
            staleRoutes++;
            queuedOffline++;
            pushNotifications++;
            System.out.println("      registry says " + owner.get() + " holds " + message.to()
                    + ", but that server has no such socket — STALE ROUTE. Message stays in "
                    + "the inbox (" + inbox.undeliveredCount() + " undelivered).");
            return;
        }

        // The recipient is online, so drain the whole backlog rather than just
        // this message. If anything was queued while they were away it has to go
        // first, otherwise the new message overtakes it and the conversation
        // reads out of order on the device.
        for (Message pending : inbox.drain()) {
            target.push(pending);
            deliveredLive++;
        }
    }

    /** Called by a server the moment a client reconnects to it. */
    public void flushInbox(String userId, ChatServer server) {
        Inbox inbox = inboxes.get(userId);
        if (inbox == null || !inbox.hasUndelivered()) {
            return;
        }
        System.out.println("      replaying " + inbox.undeliveredCount()
                + " message(s) from the inbox, in sequence order:");
        for (Message pending : inbox.drain()) {
            server.push(pending);
            deliveredLive++;
        }
    }

    public void report() {
        System.out.println("  routing report at t=" + ticker.nowMillis() + "ms");
        System.out.println("    delivered to a device      : " + deliveredLive);
        System.out.println("    queued for offline delivery: " + queuedOffline);
        System.out.println("    push notifications sent    : " + pushNotifications);
        System.out.println("    stale routes survived      : " + staleRoutes);
        System.out.println("    duplicate sends absorbed   : " + duplicatesAbsorbed);
        System.out.println("    registry                   : " + registry.stats()
                + " entries=" + registry.size());
        List<String> lines = new ArrayList<>();
        for (Inbox inbox : inboxes.values()) {
            lines.add(inbox.userId() + " total=" + inbox.total()
                    + " undelivered=" + inbox.undeliveredCount());
        }
        System.out.println("    inboxes                    : " + String.join(", ", lines));
    }
}
