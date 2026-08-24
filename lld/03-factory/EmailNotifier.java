public class EmailNotifier implements Notifier {
    private final String smtpHost;

    public EmailNotifier(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    @Override
    public void send(Message m) {
        System.out.println("  email via " + smtpHost + " -> " + m.to() + " : " + m.subject());
    }
}
