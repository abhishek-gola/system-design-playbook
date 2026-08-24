import java.util.ArrayList;
import java.util.List;

/**
 * The per-user durable inbox, and the answer to "what happens when the
 * recipient's phone is off".
 *
 * Every accepted message is appended here before anyone tries to push it. That
 * ordering is deliberate and it is the thing to say out loud: persist first,
 * deliver second. Push first and you have a message that existed only inside a
 * socket write, so a server that dies mid-delivery loses it with no trace.
 *
 * The cursor is an index into the log, not a flag on each message. It says
 * "everything before this point has reached the device". A reconnecting client
 * sends nothing but its user id; the server replays from the cursor. That keeps
 * the client dumb, keeps replay in sequence order, and means a device that has
 * been off for a week gets its backlog in the right order rather than in
 * whatever order the pushes happen to fire.
 *
 * In production this is a partitioned table or a Cassandra row keyed by
 * (userId, seq), and the cursor is one small row per device. Multi-device is
 * the natural extension: one cursor per device instead of one per user, because
 * your laptop and your phone are at different points in the same log.
 */
public class Inbox {

    private final String userId;
    private final List<Message> log = new ArrayList<>();
    private int cursor;

    public Inbox(String userId) {
        this.userId = userId;
    }

    public void append(Message message) {
        log.add(message);
    }

    public boolean hasUndelivered() {
        return cursor < log.size();
    }

    public int undeliveredCount() {
        return log.size() - cursor;
    }

    public int total() {
        return log.size();
    }

    public String userId() {
        return userId;
    }

    /**
     * Hand back everything the device has not seen, in sequence order, and move
     * the cursor.
     *
     * Moving the cursor at the point of sending is the simple version, and it is
     * the version that loses a message if the socket dies between the write and
     * the phone. The production answer is to move it when the device
     * acknowledges instead, which trades that loss for the occasional duplicate
     * on reconnect. Chat picks the duplicate every time: showing a message twice
     * is embarrassing, losing one is a bug report. Say which one you have
     * chosen and why, because the interviewer is listening for it.
     */
    public List<Message> drain() {
        List<Message> pending = new ArrayList<>(log.subList(cursor, log.size()));
        cursor = log.size();
        return pending;
    }
}
