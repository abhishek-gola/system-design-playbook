import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The chain built from an ordered list of rule names, rather than hard-wired.
 *
 * This is the part that turns the pattern into an operational answer. The list
 * comes from config, so risk analysts reorder the pipeline — or drop a rule
 * that has started false-positiving — without waiting for a deploy.
 *
 * It is also how you order by cost without anyone having to remember to: put
 * the cheap checks first in the config and the expensive scorer last, and the
 * ordering is visible in one file instead of implied by five constructors.
 */
public class RiskChain {

    private final RiskCheck head;
    private final List<String> order;

    private RiskChain(RiskCheck head, List<String> order) {
        this.head = head;
        this.order = order;
    }

    public static RiskChain fromConfig(List<String> ruleOrder,
                                       Map<String, Supplier<RiskCheck>> registry) {
        if (ruleOrder.isEmpty()) {
            throw new IllegalArgumentException("a chain needs at least one rule");
        }

        RiskCheck head = null;
        RiskCheck tail = null;
        for (String ruleName : ruleOrder) {
            Supplier<RiskCheck> supplier = registry.get(ruleName);
            if (supplier == null) {
                throw new IllegalArgumentException("unknown rule in config: " + ruleName);
            }
            RiskCheck check = supplier.get();
            if (head == null) {
                head = check;
                tail = check;
            } else {
                tail = tail.then(check);
            }
        }
        return new RiskChain(head, List.copyOf(ruleOrder));
    }

    public Decision evaluate(Txn txn) {
        return head.handle(txn);
    }

    public List<String> order() {
        return order;
    }

    /** Handy for the demo — a registry keyed by the names the config uses. */
    public static Map<String, Supplier<RiskCheck>> registry(FeatureStore features,
                                                            Map<String, Double> modelScores,
                                                            boolean modelServerDown) {
        Map<String, Supplier<RiskCheck>> registry = new LinkedHashMap<>();
        registry.put("trusted-merchant",   TrustedMerchantCheck::new);
        registry.put("card-blacklist",     () -> new BlacklistCheck(features));
        registry.put("amount-threshold",   () -> new AmountThresholdCheck(50_000, 200_000));
        registry.put("velocity-per-user",  () -> new VelocityCheck(features, 8, 20));
        registry.put("device-fingerprint", () -> new DeviceFingerprintCheck(features));
        registry.put("ml-score",           () -> new MlScoreCheck(modelScores, modelServerDown));
        return registry;
    }
}
