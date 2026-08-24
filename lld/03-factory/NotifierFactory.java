import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Simple factory. Not a GoF pattern, and the one you will actually write.
 *
 * Two versions here on purpose.
 *
 * of()          — the switch. This is the switch you are ALLOWED to keep: the
 *                 factory is the one place that is supposed to know every type,
 *                 and confining it here is the entire point. What OCP objects
 *                 to is the same switch appearing in six other files.
 *
 * fromRegistry()— suppliers in a map, so a new channel is a new entry that can
 *                 come from config with no recompile. Show this if they push on
 *                 OCP; don't lead with it, because the switch reads better and
 *                 clarity wins the first pass.
 */
public class NotifierFactory {

    private final String smtpHost;
    private final String smsGateway;
    private final String pushAppId;
    private final Map<Channel, Supplier<Notifier>> registry = new EnumMap<>(Channel.class);

    public NotifierFactory(String smtpHost, String smsGateway, String pushAppId) {
        this.smtpHost = smtpHost;
        this.smsGateway = smsGateway;
        this.pushAppId = pushAppId;

        registry.put(Channel.EMAIL, () -> new EmailNotifier(smtpHost));
        registry.put(Channel.SMS,   () -> new SmsNotifier(smsGateway));
        registry.put(Channel.PUSH,  () -> new PushNotifier(pushAppId));
    }

    public Notifier of(Channel channel) {
        return switch (channel) {
            case EMAIL -> new EmailNotifier(smtpHost);
            case SMS   -> new SmsNotifier(smsGateway);
            case PUSH  -> new PushNotifier(pushAppId);
        };
    }

    public Notifier fromRegistry(Channel channel) {
        Supplier<Notifier> supplier = registry.get(channel);
        if (supplier == null) {
            throw new IllegalArgumentException("no notifier registered for " + channel);
        }
        return supplier.get();
    }

    /** A new channel at runtime — the reason the registry version exists. */
    public void register(Channel channel, Supplier<Notifier> supplier) {
        registry.put(channel, supplier);
    }
}
