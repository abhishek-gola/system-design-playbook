/**
 * Visitor: add operations to a stable hierarchy without editing it.
 *
 * The trade-off to state, because it is the question: Visitor makes new
 * OPERATIONS cheap and new NODE TYPES expensive, which is the exact opposite of
 * ordinary polymorphism. Adding a third visitor below costs one class. Adding a
 * third expression type costs a method on every visitor that exists.
 *
 * That is why it lives in compilers, where the node types are fixed by the
 * grammar and the operations keep multiplying — parse, type-check, optimise,
 * emit — and almost nowhere else.
 *
 * It pairs naturally with Composite (lld/12-composite): a uniform tree, plus a
 * way to add reports over it without ten new methods on Node.
 */
public class VisitorDemo {

    interface Expr {
        <R> R accept(Visitor<R> visitor);
    }

    interface Visitor<R> {
        R visitNumber(Number number);
        R visitAdd(Add add);
        R visitMultiply(Multiply multiply);
    }

    record Number(int value) implements Expr {
        public <R> R accept(Visitor<R> v) { return v.visitNumber(this); }
    }

    record Add(Expr left, Expr right) implements Expr {
        public <R> R accept(Visitor<R> v) { return v.visitAdd(this); }
    }

    record Multiply(Expr left, Expr right) implements Expr {
        public <R> R accept(Visitor<R> v) { return v.visitMultiply(this); }
    }

    /** Operation one. Nothing above this line changed to add it. */
    static class Evaluate implements Visitor<Integer> {
        public Integer visitNumber(Number n)     { return n.value(); }
        public Integer visitAdd(Add a)           { return a.left().accept(this) + a.right().accept(this); }
        public Integer visitMultiply(Multiply m) { return m.left().accept(this) * m.right().accept(this); }
    }

    /** Operation two. Also one class, also no edits to the node types. */
    static class Print implements Visitor<String> {
        public String visitNumber(Number n)     { return String.valueOf(n.value()); }
        public String visitAdd(Add a)           { return "(" + a.left().accept(this) + " + " + a.right().accept(this) + ")"; }
        public String visitMultiply(Multiply m) { return m.left().accept(this) + " * " + m.right().accept(this); }
    }

    public static void show() {
        Expr expression = new Add(new Number(2), new Multiply(new Number(3), new Number(4)));

        System.out.println("    printed:   " + expression.accept(new Print()));
        System.out.println("    evaluated: " + expression.accept(new Evaluate()));
        System.out.println("    Two operations, one class each, and the three node types were");
        System.out.println("    never touched. Adding a fourth node type, though, means a new");
        System.out.println("    method on every visitor — that is the trade you are making.");
    }
}
