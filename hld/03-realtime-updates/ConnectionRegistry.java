import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * userId -> serverId, with a TTL.
 *
 * The ConcurrentHashMap stands in for Redis. In a real system this is a Redis
 * hash or a set of keys with EXPIRE, because every server in the fleet has to
 * read it on the hot path of every message and it has to survive any one server
 * dying. Nothing else about the design changes.
 *
 * The TTL is the part worth defending out loud. Without it, a server that dies
 * without running its shutdown hook leaves its users pointing at a corpse
 * forever, and those users stay undeliverable until an operator notices. With
 * it, the entry rots on its own: connected servers refresh their users every
 * few seconds, a dead server refreshes nothing, and thirty seconds later the
 * cluster has forgotten it. You pay for that with a heartbeat write per
 * connection per interval, which is the cheapest insurance in the design.
 *
 * Note what the TTL does NOT give you: correctness in the gap. Between the
 * crash and the expiry, lookups still return the dead server. The registry is a
 * routing hint, never a guarantee, and the delivery path has to cope with being
 * pointed at the wrong place. Cluster does exactly that.
 */
public class ConnectionRegistry {

    /** serverId plus the wall-clock instant at which this entry stops counting. */
    public record Entry(String serverId, long expiresAtMillis) { }

    private final Map<String, Entry> byUser = new ConcurrentHashMap<>();
    private final Ticker ticker;
    private final long ttlMillis;

    private int lookups;
    private int hits;
    private int missesBecauseExpired;

    public ConnectionRegistry(Ticker ticker, long ttlMillis) {
        this.ticker = ticker;
        this.ttlMillis = ttlMillis;
    }

    /** Called on connect, and again on every heartbeat. Same operation both times. */
    public void register(String userId, String serverId) {
        byUser.put(userId, new Entry(serverId, ticker.nowMillis() + ttlMillis));
    }

    /** Called on a clean disconnect. The TTL is the backstop for the unclean ones. */
    public void unregister(String userId) {
        byUser.remove(userId);
    }

    /**
     * Expiry is evaluated lazily on read, the way Redis mostly does it. An entry
     * that is past its TTL is invisible even if nothing has swept it yet.
     */
    public Optional<String> lookup(String userId) {
        lookups++;
        Entry entry = byUser.get(userId);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() <= ticker.nowMillis()) {
            missesBecauseExpired++;
            byUser.remove(userId, entry);
            return Optional.empty();
        }
        hits++;
        return Optional.of(entry.serverId());
    }

    /**
     * The background sweep. Redis does this itself; here it is explicit so the
     * Demo can show the moment a crashed server's entries stop existing.
     * Sorted so the output is the same on every run.
     */
    public List<String> sweepExpired() {
        long now = ticker.nowMillis();
        List<String> removed = new ArrayList<>();
        Iterator<Map.Entry<String, Entry>> it = byUser.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> e = it.next();
            if (e.getValue().expiresAtMillis() <= now) {
                removed.add(e.getKey());
                it.remove();
            }
        }
        Collections.sort(removed);
        return removed;
    }

    public int size() {
        return byUser.size();
    }

    public String stats() {
        return "lookups=" + lookups + " hits=" + hits + " expired-on-read=" + missesBecauseExpired;
    }
}
