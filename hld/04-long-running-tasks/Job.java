/**
 * The unit of work. A row in your jobs table and a message in the queue, and
 * the two are not the same thing — the table is the truth the user polls, the
 * message is a delivery attempt at it.
 *
 * jobId is the important field, and not because it identifies the job. It is
 * the idempotency key. Every write the handler performs is keyed by it, which
 * is what makes the second delivery of the same message harmless. Design that
 * in from the first minute; retrofitting idempotency onto a handler that
 * appends rather than upserts is a rewrite.
 */
public record Job(String jobId, String videoId, String resolution) {

    @Override
    public String toString() {
        return jobId + " (" + videoId + " -> " + resolution + ")";
    }
}
