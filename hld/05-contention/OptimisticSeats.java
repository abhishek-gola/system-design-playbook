import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optimistic concurrency: no locks, a version column, and an affected-row count.
 *
 * The table stands in for
 *
 *   CREATE TABLE seats (id TEXT PRIMARY KEY, owner TEXT, version INT NOT NULL);
 *
 * and claim() stands in for
 *
 *   UPDATE seats SET owner = ?, version = version + 1
 *    WHERE id = ? AND version = ?
 *
 * The whole pattern lives in the return value of that statement. One row
 * affected means you won. Zero rows affected means somebody changed the row
 * between your read and your write, and it is entirely your problem to notice
 * that and decide what to do. The failure mode candidates walk into is running
 * the UPDATE and never looking at the count, at which point the design is
 * decorative and overselling is back.
 */
public final class OptimisticSeats {

    /** Immutable row. Every claim replaces it rather than mutating in place. */
    public record Row(String seatId, String owner, int version) {
    }

    private final Map<String, Row> table = new LinkedHashMap<>();
    private int lostAttempts = 0;

    public OptimisticSeats(String... seatIds) {
        for (String id : seatIds) {
            table.put(id, new Row(id, null, 0));
        }
    }

    /** The SELECT. Note what the caller gets: the value *and* the version it was read at. */
    public Row read(String seatId) {
        return table.get(seatId);
    }

    /** @return affected row count: 1 if this writer won, 0 if somebody beat them to it. */
    public int claim(String seatId, String buyer, int expectedVersion) {
        Row current = table.get(seatId);
        if (current == null) {
            return 0;
        }
        // Only the version is checked. There is no separate "is it still free"
        // test, because any change to the row bumps the version - that is the
        // point of the column, and it is why it catches conflicts you did not
        // think to look for.
        if (current.version() != expectedVersion) {
            lostAttempts++;
            return 0;
        }
        table.put(seatId, new Row(seatId, buyer, expectedVersion + 1));
        return 1;
    }

    /** Work thrown away. This number is the cost of the pattern, and it grows with contention. */
    public int lostAttempts() {
        return lostAttempts;
    }
}
