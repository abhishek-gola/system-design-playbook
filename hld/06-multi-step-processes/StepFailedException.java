/**
 * A step could not complete.
 *
 * Worth having a named type rather than throwing RuntimeException, because the
 * orchestrator has to tell two situations apart: a step that failed, which means
 * run the compensations, and a bug in the orchestrator itself, which means stop
 * and page somebody. Catching the specific type is how you avoid quietly
 * compensating your way through a NullPointerException.
 */
public class StepFailedException extends RuntimeException {

    public StepFailedException(String message) {
        super(message);
    }
}
