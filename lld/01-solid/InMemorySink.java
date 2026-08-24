import java.util.ArrayList;
import java.util.List;

/**
 * D — the payoff.
 *
 * Because Logger depends on the Sink interface and takes its sinks through the
 * constructor, a test can hand it this and assert on captured output with no
 * disk, no clock and no cleanup. If your logger can only be tested by reading a
 * file afterwards, dependency inversion is the thing you're missing.
 */
public class InMemorySink implements Sink {
    private final List<String> lines = new ArrayList<>();

    @Override
    public void write(String line) {
        lines.add(line);
    }

    @Override
    public void close() { }

    public List<String> lines() {
        return List.copyOf(lines);
    }
}
