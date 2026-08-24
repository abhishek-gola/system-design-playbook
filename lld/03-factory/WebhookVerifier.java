public interface WebhookVerifier {
    /**
     * Real implementations HMAC the payload with the provider's signing secret.
     * The point for this folder is only that the secret is provider-specific,
     * which is exactly why these three objects have to come from one family.
     */
    boolean verify(String payload, String signature);
}
