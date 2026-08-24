import java.util.List;

/**
 * Work that outlives the request, run as a simulation.
 *
 * The shape never changes: accept, persist, enqueue, return a job id. Workers
 * pull. The client polls or gets a webhook. That part takes two minutes to
 * explain and nobody is impressed by it.
 *
 * What is actually being assessed is the two paragraphs after it — what happens
 * when a worker stalls, when a job fails, when it fails forever — so that is
 * what this Demo spends its time on. Five upload jobs go through a queue with a
 * five-second visibility timeout and a retry limit of three, and between them
 * they cover the clean path, the overrunning worker whose duplicate delivery has
 * to be absorbed, the transient failure that succeeds on its third go, and the
 * corrupt upload that ends up in the dead-letter queue.
 *
 * Everything is single-threaded against a hand-cranked clock, so the output is
 * identical on every machine and thirty seconds of queue behaviour prints in an
 * instant.
 */
public class Demo {

    private static final long STEP_MILLIS = 500L;
    private static final long VISIBILITY_TIMEOUT_MILLIS = 5_000L;
    private static final int MAX_RECEIVES = 3;
    private static final long BASE_BACKOFF_MILLIS = 1_000L;
    private static final long MAX_BACKOFF_MILLIS = 8_000L;
    private static final int MAX_TICKS = 400;

    public static void main(String[] args) {
        ManualTicker clock = new ManualTicker(0L);
        JobStore store = new JobStore();
        JobQueue queue = new JobQueue(clock, VISIBILITY_TIMEOUT_MILLIS, MAX_RECEIVES,
                BASE_BACKOFF_MILLIS, MAX_BACKOFF_MILLIS);
        TranscodeService service = new TranscodeService();

        section("1. Five uploads accepted");
        System.out.println("  The API does not transcode anything. It writes a row, puts a");
        System.out.println("  message on the queue and answers immediately. Holding the request");
        System.out.println("  open for a four-minute transcode would burn a thread per upload");
        System.out.println("  and still lose the job to a proxy timeout.");
        System.out.println();

        // Each script says how the world will treat this job: how long each
        // attempt takes, from which attempt it starts succeeding, and whether it
        // is poison. Real life supplies these; here they are written down so the
        // failure modes are reproducible.
        submit(store, queue, service, new Job("job-101", "surf-cut", "1080p"),
                new long[]{1_500}, 1, false);

        // The interesting one. Its first attempt stalls on a slow read and takes
        // 9s against a 5s lease, so the message reappears while the first worker
        // is still going and a second worker picks it up.
        submit(store, queue, service, new Job("job-102", "wedding-4k", "2160p"),
                new long[]{9_000, 1_500}, 1, false);

        // Fails twice with something transient, succeeds on the third attempt.
        submit(store, queue, service, new Job("job-103", "podcast-ep12", "720p"),
                new long[]{500}, 3, false);

        // The poison message. No number of retries will ever fix this file.
        submit(store, queue, service, new Job("job-104", "corrupt-upload", "1080p"),
                new long[]{500}, 1, true);

        submit(store, queue, service, new Job("job-105", "timelapse", "1080p"),
                new long[]{1_000}, 1, false);

        section("2. Two workers pull until the queue is empty");
        System.out.println("  Visibility timeout " + VISIBILITY_TIMEOUT_MILLIS + "ms, retry limit "
                + MAX_RECEIVES + ", backoff from " + BASE_BACKOFF_MILLIS
                + "ms doubling to a " + MAX_BACKOFF_MILLIS + "ms cap, with jitter.");
        System.out.println();

        Worker workerA = new Worker("worker-a", queue, store, service, clock);
        Worker workerB = new Worker("worker-b", queue, store, service, clock);
        List<Worker> workers = List.of(workerA, workerB);

        int ticks = 0;
        while (ticks < MAX_TICKS && (!queue.isEmpty() || anyBusy(workers))) {
            for (Worker worker : workers) {
                worker.tick(STEP_MILLIS);
            }
            clock.advance(STEP_MILLIS);
            ticks++;
        }

        section("3. Somebody has to drain the dead-letter queue");
        System.out.println("  This is the operational half of the answer and it is the half");
        System.out.println("  candidates leave out. A DLQ nobody watches is a folder of lost");
        System.out.println("  work. The real answer is: alarm on depth greater than zero, a");
        System.out.println("  human looks at the payload, and either the job is fixed and");
        System.out.println("  replayed onto the main queue or it is marked failed and the user");
        System.out.println("  is told. Both outcomes are fine. Silence is not.");
        System.out.println();
        for (QueueMessage message : queue.deadLetters()) {
            store.dead(message.job().jobId(), message.receiveCount(),
                    "failed " + message.receiveCount() + " times, needs a human");
            System.out.println("    DLQ holds " + message.job()
                    + " after " + message.receiveCount() + " deliveries");
        }

        section("Report");
        queue.report();
        System.out.println();
        System.out.println("  workers");
        for (Worker worker : workers) {
            System.out.println("    " + worker.name()
                    + "  completed=" + worker.completed()
                    + "  duplicates-absorbed=" + worker.absorbedDuplicates()
                    + "  failures=" + worker.failures());
        }
        System.out.println();
        System.out.println("  idempotency");
        System.out.println("    duplicate deliveries that reached the handler: "
                + service.absorbedDuplicates());
        System.out.println("    distinct outputs written                     : "
                + service.outputCount());
        System.out.println("    job-102 output                               : "
                + service.output("job-102"));
        System.out.println("    job-102 was delivered twice and transcoded once. That gap is the");
        System.out.println("    entire value of keying the write by job id, and it is what people");
        System.out.println("    mean when they say exactly-once is at-least-once plus idempotency.");
        System.out.println();
        store.printTable();
    }

    private static void submit(JobStore store, JobQueue queue, TranscodeService service,
                               Job job, long[] durations, int succeedFromAttempt, boolean poison) {
        service.script(job.jobId(), durations, succeedFromAttempt, poison);
        // Row first, message second. The other order gives you a worker holding
        // a message for a job that does not exist yet, which is a real bug and a
        // good thing to have an opinion about.
        store.submitted(job.jobId());
        queue.send(job);
        System.out.println("    POST /videos  ->  202 Accepted  { \"jobId\": \"" + job.jobId()
                + "\", \"poll\": \"/jobs/" + job.jobId() + "\" }");
    }

    private static boolean anyBusy(List<Worker> workers) {
        for (Worker worker : workers) {
            if (worker.busy()) {
                return true;
            }
        }
        return false;
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
