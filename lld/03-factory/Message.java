public class Message {
    private final String to;
    private final String subject;
    private final String body;

    public Message(String to, String subject, String body) {
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    public String to()      { return to; }
    public String subject() { return subject; }
    public String body()    { return body; }
}
