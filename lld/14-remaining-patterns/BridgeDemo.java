/**
 * Bridge: two dimensions that vary independently, kept in two hierarchies.
 *
 * Three shapes and three renderers is nine classes if you multiply the
 * hierarchies (SvgCircle, CanvasCircle, AsciiCircle, SvgSquare...). With a
 * bridge it is three plus three, and adding a fourth renderer costs one class
 * rather than three.
 *
 * The difference from Strategy: Bridge is a decision about the SHAPE OF THE
 * WHOLE HIERARCHY, made up front. Strategy is one varying behaviour inside one
 * class. They look the same in code and mean different things in a design
 * discussion.
 */
public class BridgeDemo {

    interface Renderer {
        String drawCircle(double radius);
        String drawSquare(double side);
    }

    static class AsciiRenderer implements Renderer {
        public String drawCircle(double r) { return "( ) r=" + r; }
        public String drawSquare(double s) { return "[ ] s=" + s; }
    }

    static class SvgRenderer implements Renderer {
        public String drawCircle(double r) { return "<circle r=\"" + r + "\"/>"; }
        public String drawSquare(double s) { return "<rect width=\"" + s + "\"/>"; }
    }

    /** The abstraction side, holding a reference across the bridge. */
    abstract static class Shape {
        protected final Renderer renderer;
        Shape(Renderer renderer) { this.renderer = renderer; }
        abstract String draw();
    }

    static class Circle extends Shape {
        private final double radius;
        Circle(Renderer renderer, double radius) { super(renderer); this.radius = radius; }
        String draw() { return renderer.drawCircle(radius); }
    }

    static class Square extends Shape {
        private final double side;
        Square(Renderer renderer, double side) { super(renderer); this.side = side; }
        String draw() { return renderer.drawSquare(side); }
    }

    public static void show() {
        for (Renderer renderer : new Renderer[]{new AsciiRenderer(), new SvgRenderer()}) {
            System.out.println("    " + new Circle(renderer, 2).draw()
                    + "   " + new Square(renderer, 3).draw());
        }
        System.out.println("    2 shapes x 2 renderers = 4 classes, not 4 combinations.");
        System.out.println("    A third renderer costs one class instead of three.");
    }
}
