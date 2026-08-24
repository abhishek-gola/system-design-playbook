import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The handler, and the reason at-least-once delivery is survivable.
 *
 * The rule is one line long: key the output by the job id, and check before you
 * write. A second delivery of the same job finds an output already recorded and
 * does nothing. No duplicate transcode, no duplicate row, no duplicate charge.
 *
 * The check-then-write below is not safe as literal code in a distributed
 * system, and it is worth saying so before anyone points it out. Two workers
 * can both read "no output" and both write. In production the same idea is
 * expressed as a single conditional write — INSERT ... ON CONFLICT DO NOTHING,
 * an UPDATE guarded by the current state, a conditional PUT with the job id as
 * the key — so that the database, not the worker, decides who won. The
 * behaviour is what is being modelled here; the atomicity belongs to the
 * storage engine.
 *
 * The second half of the rule, the one people skip: this only works if the
 * whole handler is safe to repeat. A handler that appends to a log, increments
 * a counter, or posts a webhook is not idempotent just because it checks a map
 * at the top. Design the write to be repeatable and the check becomes an
 * optimisation rather than the only defence.
 *
 * The scripted durations and failures are the simulated equivalent of a slow
 * S3, a flaky codec and a corrupt upload. They are here so the Demo can show
 * each failure mode on demand.
 */
public class TranscodeService {

    public enum Result { DONE, ALREADY_DONE, FAILED }

    private static final class Script {
        private final long[] durations;
        private final int succeedFromAttempt;
        private final boolean poison;

        private Script(long[] durations, int succeedFromAttempt, boolean poison) {
            this.durations = durations;
            this.succeedFromAttempt = succeedFromAttempt;
            this.poison = poison;
        }

        private long durationFor(int attempt) {
            int index = Math.min(attempt - 1, durations.length - 1);
            if (index < 0) {
                index = 0;
            }
            return durations[index];
        }
    }

    private final Map<String, Script> scripts = new LinkedHashMap<>();

    /** jobId -> output location. This map IS the idempotency record. */
    private final Map<String, String> outputs = new LinkedHashMap<>();

    private int absorbedDuplicates;

    public void script(String jobId, long[] durations, int succeedFromAttempt, boolean poison) {
        scripts.put(jobId, new Script(durations, succeedFromAttempt, poison));
    }

    public long workMillis(String jobId, int attempt) {
        return scripts.get(jobId).durationFor(attempt);
    }

    public Result handle(Job job, int attempt) {
        if (outputs.containsKey(job.jobId())) {
            absorbedDuplicates++;
            return Result.ALREADY_DONE;
        }
        Script script = scripts.get(job.jobId());
        if (script.poison || attempt < script.succeedFromAttempt) {
            return Result.FAILED;
        }
        outputs.put(job.jobId(), "s3://transcoded/" + job.videoId() + "/" + job.resolution() + ".m3u8");
        return Result.DONE;
    }

    public String output(String jobId) {
        return outputs.get(jobId);
    }

    public int absorbedDuplicates() {
        return absorbedDuplicates;
    }

    public int outputCount() {
        return outputs.size();
    }
}
