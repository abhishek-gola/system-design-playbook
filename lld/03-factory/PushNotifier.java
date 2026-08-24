public class PushNotifier implements Notifier {
    private final String appId;

    public PushNotifier(String appId) {
        this.appId = appId;
    }

    @Override
    public void send(Message m) {
        System.out.println("  push via fcm/" + appId + " -> " + m.to() + " : " + m.subject());
    }
}
