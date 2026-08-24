public class MetricsNotifier extends NotifierDecorator {

    private int calls;
    private int failures;

    public MetricsNotifier(Notifier inner) {
        super(inner);
    }

    @Override
    public void send(Message message) {
        calls++;
        try {
            inner.send(message);
        } catch (RuntimeException e) {
            failures++;
            throw e;                       // observe, never swallow
        }
    }

    @Override
    public String label() { return "metrics"; }

    public int calls()    { return calls; }
    public int failures() { return failures; }
}
