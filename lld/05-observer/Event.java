public class Event {
    private final String type;
    private final String payload;
    private final long sequence;

    public Event(String type, String payload, long sequence) {
        this.type = type;
        this.payload = payload;
        this.sequence = sequence;
    }

    public String type()    { return type; }
    public String payload() { return payload; }
    public long sequence()  { return sequence; }

    @Override
    public String toString() { return "#" + sequence + " " + type + "(" + payload + ")"; }
}
