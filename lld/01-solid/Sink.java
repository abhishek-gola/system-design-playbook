/**
 * I — the narrow interface everyone can honestly implement.
 *
 * Note what is NOT here: rotate(). A console sink cannot rotate and should not
 * be made to pretend it can. See RotatableSink.
 */
public interface Sink extends AutoCloseable {
    void write(String line);

    @Override
    void close();          // narrowed: no checked exception, so callers stay clean
}
