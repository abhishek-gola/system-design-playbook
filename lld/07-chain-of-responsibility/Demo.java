import java.util.List;
import java.util.Map;

public class Demo {

    public static void main(String[] args) {
        riskPipeline();
        reorderedFromConfig();
        modelServerDown();
        atmDispenser();
    }

    // ------------------------------------------------------------------

    private static final FeatureStore.InMemory FEATURES = new FeatureStore.InMemory()
            .withVelocity("user-calm", 2)
            .withVelocity("user-busy", 14)
            .withVelocity("user-bot", 240)
            .withKnownDevice("user-calm", "pixel-8")
            .withKnownDevice("user-busy", "iphone-15")
            .withKnownDevice("user-bot", "emulator-1")
            .withBlacklistedCard("card-stolen-9931");

    private static final Map<String, Double> MODEL_SCORES = Map.of(
            "TXN-5", 0.94,
            "TXN-2", 0.12);

    private static final List<String> CHEAP_FIRST = List.of(
            "trusted-merchant",
            "card-blacklist",
            "amount-threshold",
            "velocity-per-user",
            "device-fingerprint",
            "ml-score");

    private static void riskPipeline() {
        System.out.println("== Risk chain, ordered cheap to expensive ==");
        System.out.println("  " + String.join(" -> ", CHEAP_FIRST));
        System.out.println();

        RiskChain chain = RiskChain.fromConfig(CHEAP_FIRST,
                RiskChain.registry(FEATURES, MODEL_SCORES, false));

        run(chain, "clean transaction",
                new Txn("TXN-1", "user-calm", "pixel-8", 45_000, "card-ok-1", false));

        run(chain, "trusted merchant, small ticket — whitelisted, chain stops",
                new Txn("TXN-2", "user-calm", "pixel-8", 20_000, "card-ok-1", true));

        run(chain, "stolen card — blocked early, model never called",
                new Txn("TXN-3", "user-calm", "pixel-8", 90_000, "card-stolen-9931", false));

        run(chain, "busy user on a new device — two review flags, still allowed",
                new Txn("TXN-4", "user-busy", "oneplus-12", 8_000_000, "card-ok-2", false));

        run(chain, "the model catches one nothing else did",
                new Txn("TXN-5", "user-calm", "pixel-8", 60_000, "card-ok-3", false));

        run(chain, "240 transactions in an hour — blocked before the model",
                new Txn("TXN-6", "user-bot", "emulator-1", 30_000, "card-ok-4", false));
    }

    private static void reorderedFromConfig() {
        System.out.println();
        System.out.println("== The same rules, expensive one first. Watch what it costs ==");
        List<String> expensiveFirst = List.of(
                "ml-score", "card-blacklist", "velocity-per-user", "amount-threshold");
        System.out.println("  " + String.join(" -> ", expensiveFirst));

        RiskChain chain = RiskChain.fromConfig(expensiveFirst,
                RiskChain.registry(FEATURES, MODEL_SCORES, false));

        run(chain, "stolen card — the model server got called anyway, for nothing",
                new Txn("TXN-7", "user-calm", "pixel-8", 90_000, "card-stolen-9931", false));

        System.out.println("  Same verdict, one wasted network call per blocked transaction.");
        System.out.println("  At a few thousand a second that is the entire argument for");
        System.out.println("  ordering by cost, and it is one line of config either way.");
    }

    private static void modelServerDown() {
        System.out.println();
        System.out.println("== The model server is down. Cheap checks fail closed, this one open ==");
        RiskChain chain = RiskChain.fromConfig(CHEAP_FIRST,
                RiskChain.registry(FEATURES, MODEL_SCORES, true));

        run(chain, "scorer unreachable — allowed, flagged for review, not blocked",
                new Txn("TXN-8", "user-calm", "pixel-8", 45_000, "card-ok-5", false));

        System.out.println("  Blocking every payment because a model server is having a bad");
        System.out.println("  day is a self-inflicted outage. Which way a check fails is a");
        System.out.println("  property of that check, not a global setting.");
    }

    private static void atmDispenser() {
        System.out.println();
        System.out.println("== The classic version: ATM cash dispensing ==");

        DenominationHandler head = new DenominationHandler(2000, 2);
        head.then(new DenominationHandler(500, 3))
            .then(new DenominationHandler(200, 2))
            .then(new DenominationHandler(100, 4));

        CashDispenser atm = new CashDispenser(head);
        System.out.println("  notes on hand: " + atm.inventory());

        dispense(atm, 5900);
        dispense(atm, 700);
        dispense(atm, 400);
        dispense(atm, 5000);
    }

    // ------------------------------------------------------------------

    private static void run(RiskChain chain, String label, Txn txn) {
        System.out.println("  " + label);
        System.out.println("    " + txn);
        System.out.println("    " + chain.evaluate(txn));
        System.out.println();
    }

    private static void dispense(CashDispenser atm, int amount) {
        try {
            Map<Integer, Integer> notes = atm.withdraw(amount);
            System.out.println("  withdraw Rs " + amount + " -> " + notes
                    + "   left: " + atm.inventory());
        } catch (RuntimeException e) {
            System.out.println("  withdraw Rs " + amount + " -> refused: " + e.getMessage());
            System.out.println("    (and the customer still has their card and their money —");
            System.out.println("     a chain that dispensed as it went would not manage that)");
        }
    }
}
