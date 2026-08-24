/**
 * I — the extra capability, split out rather than bolted on.
 *
 * A rotation scheduler takes List<RotatableSink> and the type system does the
 * filtering. The alternative — rotate() on Sink, throwing on the ones that
 * can't — is an ISP violation that manufactures an LSP violation, and callers
 * end up writing `if (sink instanceof FileSink)`.
 */
public interface RotatableSink extends Sink {
    void rotate();
}
