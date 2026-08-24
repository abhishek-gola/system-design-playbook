import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The jobs table. This is what GET /jobs/{jobId} reads, and it is the reason
 * returning 202 with an id is a complete answer rather than a dodge: the client
 * has somewhere to look.
 *
 * Keep it separate from the queue in your head. The queue is a delivery
 * mechanism with its own retry state; this is the durable record of what the
 * user asked for and how it went. If the queue vanished you could rebuild it
 * from this table, and that is a property worth having.
 */
public class JobStore {

    public enum State { QUEUED, PROCESSING, SUCCEEDED, RETRYING, DEAD }

    public record Status(State state, int attempts, String detail) { }

    private final Map<String, Status> byId = new LinkedHashMap<>();

    public void submitted(String jobId) {
        byId.put(jobId, new Status(State.QUEUED, 0, "accepted, waiting for a worker"));
    }

    public void processing(String jobId, int attempt) {
        byId.put(jobId, new Status(State.PROCESSING, attempt, "a worker has the lease"));
    }

    public void succeeded(String jobId, int attempt, String output) {
        byId.put(jobId, new Status(State.SUCCEEDED, attempt, output));
    }

    public void retrying(String jobId, int attempt, long delayMillis) {
        byId.put(jobId, new Status(State.RETRYING, attempt,
                "failed, invisible for " + delayMillis + "ms"));
    }

    public void dead(String jobId, int attempt, String reason) {
        byId.put(jobId, new Status(State.DEAD, attempt, reason));
    }

    public void printTable() {
        System.out.println("  GET /jobs/{jobId}");
        for (Map.Entry<String, Status> entry : byId.entrySet()) {
            Status status = entry.getValue();
            System.out.println("    " + pad(entry.getKey(), 10)
                    + pad(status.state().name(), 12)
                    + "attempts=" + status.attempts()
                    + "  " + status.detail());
        }
    }

    private static String pad(String value, int width) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
