import java.util.ArrayList;
import java.util.List;

/**
 * The coordinator. It drives the steps forward and, on a failure, runs the
 * compensations for the steps that did complete, in reverse.
 *
 * Reverse order is not cosmetic. Later steps can depend on earlier ones - the
 * capture needs the authorisation to still exist - so undoing them in the order
 * they ran would try to release things that are still in use.
 *
 * A real orchestrator persists the step index after each step so it can resume
 * after its own crash, and Temporal, Step Functions or a Camunda-style engine
 * are all this class plus durable state and timers. Building your own is
 * reasonable for one workflow and a mistake for twenty.
 */
public final class SagaOrchestrator {

    private final List<SagaStep> steps;

    public SagaOrchestrator(List<SagaStep> steps) {
        this.steps = List.copyOf(steps);
    }

    /** @return true if every step committed. */
    public boolean run(SagaContext ctx) {
        List<SagaStep> completed = new ArrayList<>();

        for (SagaStep step : steps) {
            try {
                step.execute(ctx);
                completed.add(step);
                System.out.println("    forward     " + pad(step.name()) + " ok");
            } catch (StepFailedException e) {
                System.out.println("    forward     " + pad(step.name()) + " FAILED: " + e.getMessage());
                compensate(completed, ctx);
                return false;
            }
        }
        return true;
    }

    private void compensate(List<SagaStep> completed, SagaContext ctx) {
        for (int i = completed.size() - 1; i >= 0; i--) {
            SagaStep step = completed.get(i);
            try {
                step.compensate(ctx);
                System.out.println("    compensate  " + pad(step.name()) + " undone");
            } catch (RuntimeException e) {
                // A compensation that fails is the case the diagrams never show
                // and the one that actually happens. Note what this does next:
                // it stops, rather than carrying on undoing earlier steps. While
                // the money is in an unknown state you do not want to release the
                // stock as well, because then you have taken a payment you cannot
                // fulfil. Halt, leave the record as it is, and let reconciliation
                // decide. Pretending this cannot happen is how you end up with
                // money that belongs to nobody.
                System.out.println("    compensate  " + pad(step.name()) + " FAILED: " + e.getMessage());
                System.out.println("    halted      " + pad("") + " " + i
                        + " earlier step(s) left alone, record needs reconciliation");
                return;
            }
        }
    }

    private static String pad(String name) {
        return String.format("%-18s", name);
    }
}
