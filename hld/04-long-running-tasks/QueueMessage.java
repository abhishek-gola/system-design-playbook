/**
 * One delivery attempt at one job, as the queue sees it.
 *
 * Three fields do the work.
 *
 * visibleAtMillis is the lease. While it is in the future no other consumer can
 * see this message. That single field is the whole visibility-timeout mechanism,
 * and understanding that it is a timestamp rather than a lock is the difference
 * between explaining SQS and reciting it. Nothing is held, nothing blocks, and
 * nobody is notified when it expires — the message simply becomes visible again
 * and the next poll finds it.
 *
 * receiveCount is how the dead-letter queue knows when to give up.
 *
 * receiptId is a fresh handle issued on every receive, and it is the detail
 * candidates miss. A worker that overran its lease still holds the receipt from
 * the previous delivery; when it eventually finishes and tries to delete the
 * message, the receipt no longer matches and the delete is refused. That refusal
 * is not an error, it is the system telling the worker that somebody else
 * already owns this work.
 */
public class QueueMessage {

    private final Job job;
    private long visibleAtMillis;
    private int receiveCount;
    private long receiptId;

    QueueMessage(Job job, long visibleAtMillis) {
        this.job = job;
        this.visibleAtMillis = visibleAtMillis;
    }

    public Job job() {
        return job;
    }

    public long visibleAtMillis() {
        return visibleAtMillis;
    }

    public int receiveCount() {
        return receiveCount;
    }

    public long receiptId() {
        return receiptId;
    }

    void markReceived(long newVisibleAtMillis, long newReceiptId) {
        this.receiveCount++;
        this.visibleAtMillis = newVisibleAtMillis;
        this.receiptId = newReceiptId;
    }

    void setVisibleAt(long millis) {
        this.visibleAtMillis = millis;
    }
}
