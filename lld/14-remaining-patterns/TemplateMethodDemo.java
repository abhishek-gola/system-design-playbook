import java.util.List;

/**
 * Template Method: the base class fixes the ORDER with a final method, and
 * subclasses fill in the steps.
 *
 * final is the whole point. Without it a subclass can override run() and skip
 * validation, and the guarantee the base class was offering evaporates.
 */
public abstract class TemplateMethodDemo {

    public final String run(String raw) {          // fixed skeleton
        String parsed = parse(raw);
        validate(parsed);
        return transform(parsed);
    }

    protected abstract String parse(String raw);
    protected abstract String transform(String parsed);

    /** A hook with a default. Subclasses override only if they care. */
    protected void validate(String parsed) {
        if (parsed.isBlank()) throw new IllegalArgumentException("empty after parse");
    }

    public static void show() {
        List<TemplateMethodDemo> importers = List.of(new CsvImporter(), new JsonImporter());
        for (TemplateMethodDemo importer : importers) {
            System.out.println("    " + importer.getClass().getSimpleName()
                    + " -> " + importer.run(importer instanceof CsvImporter
                            ? "name,bengaluru" : "{\"city\":\"bengaluru\"}"));
        }
        System.out.println("    Same three steps in the same order, two different middles.");
        System.out.println("    Prefer Strategy unless the ORDER is the thing you're fixing.");
    }
}

class CsvImporter extends TemplateMethodDemo {
    @Override protected String parse(String raw)      { return raw.split(",")[1]; }
    @Override protected String transform(String city) { return city.toUpperCase(); }
}

class JsonImporter extends TemplateMethodDemo {
    @Override protected String parse(String raw) {
        return raw.replaceAll(".*:\"", "").replaceAll("\".*", "");
    }
    @Override protected String transform(String city) { return city.toUpperCase(); }
}
