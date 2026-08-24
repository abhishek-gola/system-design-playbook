/**
 * The shape, stated once.
 *
 * A decorator IMPLEMENTS the interface and HOLDS the interface. Same in, same
 * out. That symmetry is the whole pattern, and it is what lets these nest in
 * any order — every decorator is a valid argument to every other decorator.
 *
 * If a subclass ever needs `if (inner instanceof EmailNotifier)`, the
 * abstraction is wrong. Usually the interface is too narrow and the decorator
 * needs information it does not carry. Fix the interface; do not add the
 * instanceof.
 */
public abstract class NotifierDecorator implements Notifier {

    protected final Notifier inner;

    protected NotifierDecorator(Notifier inner) {
        this.inner = inner;
    }

    public abstract String label();
}
