public class ConsoleSink implements Sink {
    private final String prefix;

    public ConsoleSink(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public void write(String line) {
        System.out.println(prefix + line);
    }

    @Override
    public void close() {
        // Nothing to release. Not a no-op that hides a problem — there genuinely
        // is no resource here, which is why close() belongs on Sink and rotate()
        // does not.
    }
}
