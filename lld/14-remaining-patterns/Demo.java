public class Demo {

    public static void main(String[] args) {
        section("Template Method — fix the order, vary the steps");
        TemplateMethodDemo.show();

        section("Proxy — control access, not add features");
        ProxyDemo.show();

        section("Facade — one door onto a subsystem you can still walk around");
        FacadeDemo.show();

        section("Iterator — traverse without exposing the internals");
        IteratorDemo.show();

        section("Flyweight — share the intrinsic, pass the extrinsic");
        FlyweightDemo.show();

        section("Bridge — two hierarchies instead of their product");
        BridgeDemo.show();

        section("Mediator — n spokes instead of n-squared references");
        MediatorDemo.show();

        section("Memento — snapshot when there is no inverse");
        MementoDemo.show();

        section("Prototype — copy rather than construct");
        PrototypeDemo.show();

        section("Visitor — cheap new operations, expensive new node types");
        VisitorDemo.show();

        System.out.println();
        System.out.println("== The four comparisons that actually get asked ==");
        System.out.println("  Proxy vs Decorator      -> intent, not structure");
        System.out.println("  Template Method vs Strategy -> fixes the order vs replaces behaviour");
        System.out.println("  Flyweight               -> intrinsic vs extrinsic, that is the whole idea");
        System.out.println("  Visitor                 -> new operations cheap, new types expensive");
        System.out.println();
        System.out.println("  Everything else on this page is recognition. Do not spend a study");
        System.out.println("  session here until strategy, factory, observer, state, chain and");
        System.out.println("  concurrency are automatic.");
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
