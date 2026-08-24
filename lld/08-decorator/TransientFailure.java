/** The kind of failure a retry can fix. A declined card is not one of these. */
public class TransientFailure extends RuntimeException {
    public TransientFailure(String message) {
        super(message);
    }
}
