/**
 * A chat message. Two of these fields carry most of the design weight, and both
 * of them are follow-up questions before they are fields.
 *
 * clientMessageId is generated on the phone, before the message leaves it. That
 * is what makes a retry over a flaky network safe: the server recognises an id
 * it has already accepted and drops the second copy instead of showing Bob the
 * same "on my way" twice. Generating the id server-side cannot work, because
 * the client has no way to tell a lost request from a lost response, so it
 * would retry either way and get a fresh id the second time.
 *
 * seq is a per-conversation monotonic counter, assigned by the server at the
 * moment it accepts the message. Wall-clock timestamps cannot do this job. Two
 * servers whose clocks differ by 40ms will disagree about which message came
 * first, and the interviewer will ask about exactly that.
 */
public record Message(String clientMessageId,
                      String conversationId,
                      String from,
                      String to,
                      String body,
                      long seq) {

    /** What the client sends. The sequence number is not its business. */
    public static Message draft(String clientMessageId, String conversationId,
                                String from, String to, String body) {
        return new Message(clientMessageId, conversationId, from, to, body, -1L);
    }

    public Message withSeq(long assigned) {
        return new Message(clientMessageId, conversationId, from, to, body, assigned);
    }

    @Override
    public String toString() {
        return conversationId + "#" + seq + " " + from + " -> " + to + "  \"" + body + "\"";
    }
}
