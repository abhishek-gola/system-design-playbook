import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Demo {

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("solid-logging-");
        Path logFile = dir.resolve("app.log");

        System.out.println("== O: three destinations, and Logger knows about none of them ==");
        FileSink file = new FileSink(logFile);
        Logger app = new Logger("orders", LogLevel.INFO, new PlainFormatter(),
                List.of(new ConsoleSink("  console | "), file));

        app.with("region", "in-south").with("build", "4471");
        app.debug("this one is below the threshold and never reaches a sink");
        app.info("order placed");
        app.warn("payment gateway slow, 1400ms");
        app.error("payment declined");

        System.out.println();
        System.out.println("== I: only the file sink can rotate, and the type says so ==");
        for (Sink sink : List.<Sink>of(new ConsoleSink(""), file)) {
            if (sink instanceof RotatableSink r) {
                r.rotate();
                System.out.println("  rotated " + sink.getClass().getSimpleName());
            } else {
                System.out.println("  skipped " + sink.getClass().getSimpleName()
                        + " — not rotatable, and it never had to pretend it was");
            }
        }
        app.info("first line after rotation");
        app.close();

        System.out.println();
        System.out.println("  " + logFile + "        -> " + Files.readAllLines(logFile).size() + " line(s)");
        System.out.println("  " + logFile + ".1      -> " + Files.readAllLines(
                logFile.resolveSibling("app.log.1")).size() + " line(s), archived");

        System.out.println();
        System.out.println("== O again: a new format is a new class, Logger is untouched ==");
        Logger audit = new Logger("audit", LogLevel.INFO, new JsonFormatter(),
                List.of(new ConsoleSink("  json    | ")));
        audit.with("actor", "ops-user-3").info("refund approved");
        audit.close();

        System.out.println();
        System.out.println("== D: the same Logger, unit-tested with no I/O at all ==");
        InMemorySink captured = new InMemorySink();
        Logger underTest = new Logger("test", LogLevel.WARN, new PlainFormatter(), List.of(captured));
        underTest.info("dropped by the threshold");
        underTest.error("boom");

        System.out.println("  captured " + captured.lines().size() + " line, as expected");
        System.out.println("  " + captured.lines().get(0));
        System.out.println();
        System.out.println("  No disk was touched. That is the whole argument for DIP,");
        System.out.println("  and it is a much better answer than reciting the definition.");
    }
}
