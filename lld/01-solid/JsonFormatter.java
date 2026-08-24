/**
 * Hand-rolled so the folder stays dependency-free. In real code this is Jackson,
 * and saying "I'd use a real serialiser here" is the correct interview answer —
 * writing your own JSON escaper is a bug farm.
 */
public class JsonFormatter implements Formatter {
    @Override
    public String format(LogMessage m) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"ts\":\"").append(m.at()).append("\",");
        sb.append("\"level\":\"").append(m.level()).append("\",");
        sb.append("\"logger\":\"").append(m.logger()).append("\",");
        sb.append("\"msg\":\"").append(escape(m.text())).append('"');
        m.context().forEach((k, v) ->
                sb.append(",\"").append(escape(k)).append("\":\"").append(escape(v)).append('"'));
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
