/**
 * The one handler that can return ALLOW, short-circuiting everything behind it.
 *
 * This is what distinguishes a chain from a plain list of validators: a handler
 * gets to decide whether the rest of the chain runs at all. If none of your
 * handlers ever do this, use a for-loop over a List<Validator> and say so —
 * reaching for the heavier pattern when the lighter one fits gets marked down.
 */
public class TrustedMerchantCheck extends RiskCheck {

    private static final long SMALL_AMOUNT_PAISE = 50_000;   // Rs 500

    @Override
    public String rule() { return "trusted-merchant"; }

    @Override
    protected Decision evaluate(Txn txn) {
        if (txn.trustedMerchant() && txn.amountPaise() <= SMALL_AMOUNT_PAISE) {
            return Decision.allow("trusted merchant, small ticket — skipping the rest");
        }
        return Decision.cont();
    }
}
