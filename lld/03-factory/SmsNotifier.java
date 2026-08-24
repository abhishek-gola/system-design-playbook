public class SmsNotifier implements Notifier {
    private final String gateway;

    public SmsNotifier(String gateway) {
        this.gateway = gateway;
    }

    @Override
    public void send(Message m) {
        System.out.println("  sms via " + gateway + " -> " + m.to() + " : " + m.body());
    }
}
