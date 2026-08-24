public class Message {
    private final String to;
    private final String text;

    public Message(String to, String text) {
        this.to = to;
        this.text = text;
    }

    public String to()   { return to; }
    public String text() { return text; }

    @Override
    public String toString() { return "'" + text + "' -> " + to; }
}
