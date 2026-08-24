/**
 * A fixed quota of sends. Simplified on purpose — the real thing is in
 * lld/02-strategy, and a decorator wrapping a strategy is a perfectly good
 * answer if they ask you to combine the two.
 */
public class RateLimitedNotifier extends NotifierDecorator {

    private int tokens;
    private int rejected;

    public RateLimitedNotifier(Notifier inner) {
        this(inner, 4);
    }

    public RateLimitedNotifier(Notifier inner, int quota) {
        super(inner);
        this.tokens = quota;
    }

    @Override
    public void send(Message message) {
        if (tokens <= 0) {
            rejected++;
            throw new IllegalStateException("rate limit exhausted");
        }
        tokens--;
        inner.send(message);
    }

    @Override
    public String label() { return "rateLimit"; }

    public int tokensLeft() { return tokens; }
    public int rejected()   { return rejected; }
}
