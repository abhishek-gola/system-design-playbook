import java.util.ArrayList;
import java.util.List;

/**
 * The test seam.
 *
 * Because OrderService depends on the PaymentGateway interface rather than on
 * an SDK class, a test can hand it this and simulate a decline on demand. No
 * network, no sandbox account, no waiting.
 *
 * Without the interface you would be mocking a class you do not own, in a
 * library that can change its shape in a minor version.
 */
public class FakeGateway implements PaymentGateway {

    private final List<ChargeResult> scripted = new ArrayList<>();
    private final List<String> seenKeys = new ArrayList<>();
    private int index;
    private boolean healthy = true;

    public FakeGateway willReturn(ChargeResult result) {
        scripted.add(result);
        return this;
    }

    public FakeGateway unhealthy() {
        this.healthy = false;
        return this;
    }

    @Override
    public String name() { return "fake"; }

    @Override
    public ChargeResult charge(Money amount, Instrument instrument, IdempotencyKey key) {
        seenKeys.add(key.value());
        if (index >= scripted.size()) {
            throw new AssertionError("FakeGateway called more times than scripted");
        }
        return scripted.get(index++);
    }

    @Override
    public boolean isHealthy() { return healthy; }

    public List<String> seenKeys() { return List.copyOf(seenKeys); }
    public int callCount()         { return index; }
}
