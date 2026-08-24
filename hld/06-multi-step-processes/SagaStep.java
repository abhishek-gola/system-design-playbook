/**
 * One step of the saga: something that commits locally, and the action that
 * undoes it.
 *
 * The compensating action is not a rollback. A rollback un-happens a change; a
 * compensation is a new change that makes up for the old one. You cannot
 * un-capture a payment, you can only refund it, and the refund is a fact in the
 * ledger that the customer will see on their statement. Say that in the
 * interview - it is the difference between having read the word saga and having
 * built one.
 *
 * This is the same shape as a Command with an undo, from
 * lld/13-command. A saga step is that command with a network
 * between the caller and the receiver, which is what makes everything hard.
 */
public interface SagaStep {

    String name();

    /** Throws StepFailedException if the step could not complete. */
    void execute(SagaContext ctx);

    /**
     * Undo the effect of execute. Must be safe to call twice, because the
     * orchestrator itself can crash mid-compensation and start again.
     */
    void compensate(SagaContext ctx);
}
