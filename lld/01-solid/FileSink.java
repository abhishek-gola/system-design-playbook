import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * The only sink that can rotate, so the only one that implements RotatableSink.
 *
 * L — note that write() after close() throws IllegalStateException, and every
 * other Sink in this folder honours the same rule. That consistency is the
 * Liskov contract. The day one subclass decides to silently ignore writes after
 * close, callers start needing to know which sink they hold.
 */
public class FileSink implements RotatableSink {
    private final Path path;
    private BufferedWriter writer;
    private int rotations;

    public FileSink(Path path) {
        this.path = path;
        this.writer = open(path);
    }

    private static BufferedWriter open(Path p) {
        try {
            Files.createDirectories(p.getParent());
            return Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot open " + p, e);
        }
    }

    @Override
    public void write(String line) {
        if (writer == null) throw new IllegalStateException("sink is closed");
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();      // a real sink batches; flushing per line keeps the demo honest
        } catch (IOException e) {
            throw new UncheckedIOException("write failed to " + path, e);
        }
    }

    @Override
    public void rotate() {
        if (writer == null) throw new IllegalStateException("sink is closed");
        try {
            writer.close();
            Path archived = path.resolveSibling(path.getFileName() + "." + (++rotations));
            Files.move(path, archived, StandardCopyOption.REPLACE_EXISTING);
            writer = open(path);
        } catch (IOException e) {
            throw new UncheckedIOException("rotate failed for " + path, e);
        }
    }

    @Override
    public void close() {
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException("close failed for " + path, e);
        } finally {
            writer = null;
        }
    }

    public Path path() { return path; }
}
