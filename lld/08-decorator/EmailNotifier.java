/**
 * The base component: the only class here that does the actual work.
 *
 * Deliberately flaky, on a fixed schedule so the demo is reproducible — the
 * first attempt at each message fails, the second succeeds. That is enough to
 * show retry behaviour without any randomness.
 */
public class EmailNotifier implements Notifier {
    private int attempts;
    private int delivered;

    @Override
    public void send(Message message) {
        attempts++;
        if (attempts % 2 == 1) {
            throw new TransientFailure("smtp 421, try again");
        }
        delivered++;
    }

    public int attempts()  { return attempts; }
    public int delivered() { return delivered; }

    public void reset() {
        attempts = 0;
        delivered = 0;
    }
}
