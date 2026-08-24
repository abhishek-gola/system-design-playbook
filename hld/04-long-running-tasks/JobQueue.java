import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * An SQS-shaped work queue, small enough to read in one sitting.
 *
 * Everything here exists because of one assumption: a worker can die, stall or
 * be network-partitioned at any moment, and the queue will never be told. Given
 * that, a message cannot be removed when it is handed out — it has to be hidden
 * for a while and then reappear if nobody confirms. That is the visibility
 * timeout, and every other feature in this class follows from it.
 *
 * The consequence, which you should say out loud before the interviewer asks:
 * this is at-least-once delivery. A message will occasionally be processed
 * twice. There is no configuration that prevents it, so the handler has to be
 * idempotent instead. See TranscodeService.
 *
 * The linear scan in receive() is not how a real broker works — SQS keeps
 * per-partition structures and Kafka does not have per-message visibility at
 * all. It is written this way because the timing rules are the lesson and a
 * priority queue would hide them.
 */
public class JobQueue {

    public enum DeleteResult { DELETED, STALE_RECEIPT, ALREADY_GONE }

    private final Ticker ticker;
    private final long visibilityTimeoutMillis;
    private final int maxReceives;
    private final long baseBackoffMillis;
    private final long maxBackoffMillis;

    /** Fixed seed, because a demo whose output changes between runs is not a demo. */
    private final Random jitter = new Random(42);

    private final List<QueueMessage> inQueue = new ArrayList<>();
    private final List<QueueMessage> deadLetters = new ArrayList<>();

    private long nextReceiptId = 1L;

    private int sent;
    private int received;
    private int deleted;
    private int refusedDeletes;
    private int retries;

    public JobQueue(Ticker ticker, long visibilityTimeoutMillis, int maxReceives,
                    long baseBackoffMillis, long maxBackoffMillis) {
        this.ticker = ticker;
        this.visibilityTimeoutMillis = visibilityTimeoutMillis;
        this.maxReceives = maxReceives;
        this.baseBackoffMillis = baseBackoffMillis;
        this.maxBackoffMillis = maxBackoffMillis;
    }

    public void send(Job job) {
        inQueue.add(new QueueMessage(job, ticker.nowMillis()));
        sent++;
    }

    /**
     * Hand out the first message whose lease or backoff window has passed.
     *
     * The dead-letter check sits here rather than at the point of failure on
     * purpose, and it matches how SQS actually behaves: a message is moved to
     * the DLQ when it is next *received*, not when it last failed. That is why a
     * poison message with a long backoff can sit in the queue for a while after
     * its final failure before it shows up in the DLQ.
     */
    public QueueMessage receive() {
        long now = ticker.nowMillis();
        Iterator<QueueMessage> it = inQueue.iterator();
        while (it.hasNext()) {
            QueueMessage message = it.next();
            if (message.visibleAtMillis() > now) {
                continue;
            }
            if (message.receiveCount() >= maxReceives) {
                it.remove();
                deadLetters.add(message);
                System.out.println("    [queue] " + message.job().jobId() + " has been delivered "
                        + message.receiveCount() + " times and failed every time"
                        + " — moved to the dead-letter queue, not retried again");
                continue;
            }
            message.markReceived(now + visibilityTimeoutMillis, nextReceiptId++);
            received++;
            return message;
        }
        return null;
    }

    /**
     * Confirm the work. The receipt check is what makes a late delete from an
     * overrunning worker a no-op instead of a corruption.
     */
    public DeleteResult delete(QueueMessage message, long receiptId) {
        if (message.receiptId() != receiptId) {
            refusedDeletes++;
            return DeleteResult.STALE_RECEIPT;
        }
        if (!inQueue.remove(message)) {
            refusedDeletes++;
            return DeleteResult.ALREADY_GONE;
        }
        deleted++;
        return DeleteResult.DELETED;
    }

    /**
     * Give up on this attempt and make the message visible again after a
     * backoff. Returns the delay, or -1 if the caller's lease had already
     * expired and somebody else now owns the message.
     */
    public long retryLater(QueueMessage message, long receiptId) {
        if (message.receiptId() != receiptId) {
            return -1L;
        }
        long delay = backoffMillis(message.receiveCount());
        message.setVisibleAt(ticker.nowMillis() + delay);
        retries++;
        return delay;
    }

    /**
     * Exponential backoff with jitter, and the jitter is the half that matters.
     *
     * Plain doubling means every worker that failed during the same downstream
     * outage retries at the same instant, so the service that just fell over
     * gets a synchronised wall of traffic the moment it comes back and falls
     * over again. Spreading the retries across a window turns that spike into a
     * ramp. This is equal jitter — half the delay fixed, half random — which
     * keeps a floor under the delay while still smearing the herd.
     */
    private long backoffMillis(int receiveCount) {
        int exponent = Math.min(receiveCount - 1, 16);
        long capped = Math.min(maxBackoffMillis, baseBackoffMillis * (1L << exponent));
        long half = capped / 2;
        return half + jitter.nextInt((int) half + 1);
    }

    public boolean isEmpty() {
        return inQueue.isEmpty();
    }

    public List<QueueMessage> deadLetters() {
        return Collections.unmodifiableList(deadLetters);
    }

    public void report() {
        System.out.println("  queue");
        System.out.println("    messages sent        : " + sent);
        System.out.println("    deliveries made      : " + received
                + "   (more than sent — that is at-least-once working, not a bug)");
        System.out.println("    deletes accepted     : " + deleted);
        System.out.println("    deletes refused      : " + refusedDeletes
                + "   (a stale receipt from a worker that overran its lease)");
        System.out.println("    retries scheduled    : " + retries);
        System.out.println("    dead-lettered        : " + deadLetters.size());
    }
}
