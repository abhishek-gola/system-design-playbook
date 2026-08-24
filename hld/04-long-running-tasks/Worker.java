/**
 * A worker that pulls.
 *
 * Pull rather than push is the choice to name. A pushed work queue delivers at
 * whatever rate the producer manages, so a burst of uploads takes the transcode
 * fleet down with it. A pulled one delivers exactly as fast as the fleet asks
 * for work, which means the queue depth absorbs the burst and the only thing
 * that suffers is latency. Backpressure for free, and the queue depth doubles as
 * the autoscaling signal.
 *
 * This worker is a state machine ticked by the Demo's loop rather than a thread.
 * Threads would add nothing except interleaved output and a demo that prints
 * something different every run. The behaviour being shown — a lease expiring
 * under a worker that is still working — is a timing property, and a hand-cranked
 * clock demonstrates it far more clearly than a real one.
 */
public class Worker {

    private final String name;
    private final JobQueue queue;
    private final JobStore store;
    private final TranscodeService service;
    private final Ticker ticker;

    private QueueMessage inFlight;
    private long receiptId;
    private long remainingWorkMillis;
    private int attempt;

    private int completed;
    private int absorbedDuplicates;
    private int failures;

    public Worker(String name, JobQueue queue, JobStore store,
                  TranscodeService service, Ticker ticker) {
        this.name = name;
        this.queue = queue;
        this.store = store;
        this.service = service;
        this.ticker = ticker;
    }

    public boolean busy() {
        return inFlight != null;
    }

    public void tick(long stepMillis) {
        if (inFlight == null) {
            pollForWork();
            return;
        }
        remainingWorkMillis -= stepMillis;
        if (remainingWorkMillis <= 0) {
            finish();
        }
    }

    private void pollForWork() {
        QueueMessage message = queue.receive();
        if (message == null) {
            return;
        }
        inFlight = message;
        receiptId = message.receiptId();
        attempt = message.receiveCount();
        remainingWorkMillis = service.workMillis(message.job().jobId(), attempt);
        store.processing(message.job().jobId(), attempt);

        long leaseMillis = message.visibleAtMillis() - ticker.nowMillis();
        String warning = remainingWorkMillis > leaseMillis
                ? "  <-- this will overrun the lease, and the queue will hand it to somebody else"
                : "";
        log("picked up " + message.job() + " attempt " + attempt
                + ", needs " + remainingWorkMillis + "ms, lease runs " + leaseMillis + "ms" + warning);
    }

    private void finish() {
        Job job = inFlight.job();
        TranscodeService.Result result = service.handle(job, attempt);

        switch (result) {
            case DONE -> {
                completed++;
                store.succeeded(job.jobId(), attempt, service.output(job.jobId()));
                log("finished " + job.jobId() + " -> " + service.output(job.jobId()));
                confirm();
            }
            case ALREADY_DONE -> {
                // The moment the whole design is built for. This worker did the
                // work honestly and arrived to find it already done. Nothing is
                // broken and nothing needs fixing, because the handler refused
                // to write twice.
                absorbedDuplicates++;
                log("finished " + job.jobId() + " and found an output already recorded"
                        + " — duplicate delivery absorbed, no second write");
                confirm();
            }
            case FAILED -> {
                failures++;
                long delay = queue.retryLater(inFlight, receiptId);
                if (delay < 0) {
                    log(job.jobId() + " failed, but our lease had already expired"
                            + " — another worker owns it now, so we say nothing");
                } else {
                    store.retrying(job.jobId(), attempt, delay);
                    log(job.jobId() + " failed on attempt " + attempt
                            + " — invisible for " + delay + "ms, then it comes back");
                }
            }
        }
        inFlight = null;
    }

    private void confirm() {
        JobQueue.DeleteResult result = queue.delete(inFlight, receiptId);
        switch (result) {
            case DELETED -> log("deleted " + inFlight.job().jobId() + " from the queue");
            case STALE_RECEIPT -> log("delete refused: our receipt is stale."
                    + " The lease expired while we were working and another worker"
                    + " already finished and deleted this. Correct outcome, no action.");
            case ALREADY_GONE -> log("message was already gone from the queue");
        }
    }

    private void log(String message) {
        System.out.println("    [t=" + pad(ticker.nowMillis()) + "ms] " + name + " " + message);
    }

    private static String pad(long millis) {
        String value = Long.toString(millis);
        StringBuilder sb = new StringBuilder();
        while (sb.length() + value.length() < 5) {
            sb.append(' ');
        }
        return sb.toString() + value;
    }

    public String name() {
        return name;
    }

    public int completed() {
        return completed;
    }

    public int absorbedDuplicates() {
        return absorbedDuplicates;
    }

    public int failures() {
        return failures;
    }
}
