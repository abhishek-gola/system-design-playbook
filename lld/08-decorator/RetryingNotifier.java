public class RetryingNotifier extends NotifierDecorator {

    private final int maxAttempts;
    private int retries;

    public RetryingNotifier(Notifier inner) {
        this(inner, 3);
    }

    public RetryingNotifier(Notifier inner, int maxAttempts) {
        super(inner);
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void send(Message message) {
        TransientFailure last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                inner.send(message);
                return;
            } catch (TransientFailure e) {
                // Only transient failures are retried. A rate limit rejection or
                // a declined card propagates immediately, because retrying it
                // just burns the budget and delays the error the caller needs.
                last = e;
                retries++;
            }
        }
        throw new IllegalStateException("gave up after " + maxAttempts
                + " attempts: " + (last == null ? "" : last.getMessage()));
    }

    @Override
    public String label() { return "retry"; }

    public int retries() { return retries; }
}
