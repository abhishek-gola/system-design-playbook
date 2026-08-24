public class PlainFormatter implements Formatter {
    @Override
    public String format(LogMessage m) {
        StringBuilder sb = new StringBuilder()
                .append(m.at()).append(' ')
                .append(pad(m.level().name())).append(' ')
                .append('[').append(m.logger()).append("] ")
                .append(m.text());
        m.context().forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        return sb.toString();
    }

    private static String pad(String level) {
        return (level + "    ").substring(0, 5);
    }
}
