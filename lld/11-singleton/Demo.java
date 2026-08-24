import java.lang.reflect.Constructor;
import java.util.Map;

public class Demo {

    public static void main(String[] args) throws Exception {
        whenEachOneInitialises();
        theEnumSurvivesReflection();
        whyInjectionWins();
    }

    private static void whenEachOneInitialises() {
        System.out.println("== Four implementations, and when each one runs its constructor ==");

        System.out.println("  touching EagerConfig for the first time:");
        EagerConfig.get();
        System.out.println("    (it had already been constructed when the class loaded)");

        System.out.println("  touching LazyHolderConfig for the first time:");
        LazyHolderConfig.get();
        System.out.println("  and again:");
        LazyHolderConfig.get();
        System.out.println("    (silence — the holder class was already initialised)");

        System.out.println("  touching DoubleCheckedConfig for the first time:");
        DoubleCheckedConfig.get();
        System.out.println("  and again:");
        DoubleCheckedConfig.get();

        System.out.println("  all four return the same object every time:");
        System.out.println("    eager:  " + (EagerConfig.get() == EagerConfig.get()));
        System.out.println("    holder: " + (LazyHolderConfig.get() == LazyHolderConfig.get()));
        System.out.println("    dcl:    " + (DoubleCheckedConfig.get() == DoubleCheckedConfig.get()));
        System.out.println("    enum:   " + (EnumConfig.INSTANCE == EnumConfig.valueOf("INSTANCE")));
        System.out.println();
    }

    private static void theEnumSurvivesReflection() throws Exception {
        System.out.println("== The attack that defeats three of the four ==");

        Constructor<LazyHolderConfig> constructor =
                LazyHolderConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LazyHolderConfig smuggled = constructor.newInstance();

        System.out.println("  reflection on LazyHolderConfig produced a second instance: "
                + (smuggled != LazyHolderConfig.get()));
        System.out.println("  so much for 'exactly one'.");

        System.out.println("  the same attack on the enum:");
        try {
            Constructor<?>[] enumConstructors = EnumConfig.class.getDeclaredConstructors();
            Constructor<?> enumConstructor = enumConstructors[0];
            enumConstructor.setAccessible(true);
            enumConstructor.newInstance();
            System.out.println("    it worked, which should not happen");
        } catch (Exception e) {
            System.out.println("    refused: " + e.getClass().getSimpleName());
            System.out.println("    The JVM will not construct an enum reflectively, and");
            System.out.println("    deserialising one gives you back the same constant. That");
            System.out.println("    is the whole reason Effective Java recommends it.");
        }
        System.out.println();
    }

    private static void whyInjectionWins() {
        System.out.println("== Why you'd inject one instead ==");

        InjectedConfig production = new InjectedConfig(
                Map.of("region", "in-south", "env", "production"));
        InjectedConfig underTest = new InjectedConfig(
                Map.of("region", "test", "env", "test"));

        System.out.println("  production instance: region=" + production.get("region"));
        System.out.println("  test instance:       region=" + underTest.get("region"));
        System.out.println();
        System.out.println("  Two instances, on purpose, and neither test can affect the");
        System.out.println("  other through shared state. Exactly one still exists in");
        System.out.println("  production — the wiring guarantees it — but the class does not");
        System.out.println("  enforce it, so nothing has a hidden dependency on a static.");
        System.out.println();
        System.out.println("  The tell that you needed this: two tests that pass alone and");
        System.out.println("  fail together. That is almost always shared singleton state,");
        System.out.println("  and the fix is never a reset() method, because now you have to");
        System.out.println("  remember to call it.");
        System.out.println();
        System.out.println("  Global state via the enum, for comparison — reads: "
                + EnumConfig.INSTANCE.reads() + ", shared by every test in the JVM.");
    }
}
