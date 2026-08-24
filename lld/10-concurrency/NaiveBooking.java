import java.util.HashMap;
import java.util.Map;

/**
 * The bug, on purpose.
 *
 * containsKey() then put() is check-then-act: two steps with a window between
 * them. Every thread that gets through the check before any of them writes will
 * believe it won the seat.
 *
 * The busy-wait in the middle only widens the window so the demo reproduces
 * reliably. It does not create the bug — removing it makes the race rarer and
 * therefore worse, because a race that shows up once a month in production is
 * far more expensive than one that shows up in every test run.
 */
public class NaiveBooking {

    private final Map<String, String> owner = new HashMap<>();

    public boolean book(String seatId, String userId) {
        if (!owner.containsKey(seatId)) {
            widenTheWindow();
            owner.put(seatId, userId);
            return true;
        }
        return false;
    }

    private void widenTheWindow() {
        for (int i = 0; i < 200; i++) {
            Thread.onSpinWait();
        }
    }

    public String ownerOf(String seatId) {
        return owner.get(seatId);
    }
}
