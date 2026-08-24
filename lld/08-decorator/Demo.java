import java.util.List;
import java.util.function.UnaryOperator;

public class Demo {

    public static void main(String[] args) {
        System.out.println("== The same three features, stacked three ways ==");
        System.out.println("  The transport fails every other attempt, deterministically.");
        System.out.println("  Quota is 4 sends. We try to send 3 messages each time.");
        System.out.println();

        retryOutside();
        limiterOutside();
        metricsOutside();
        builtFromAList();
        theInheritanceAlternative();
    }

    // ------------------------------------------------------------------

    private static void retryOutside() {
        System.out.println("  1. retry( rateLimit( metrics( email ) ) )");
        EmailNotifier email = new EmailNotifier();
        MetricsNotifier metrics = new MetricsNotifier(email);
        RateLimitedNotifier limiter = new RateLimitedNotifier(metrics, 4);
        RetryingNotifier retry = new RetryingNotifier(limiter, 3);

        int sent = attemptThree(retry);

        System.out.println("     delivered " + sent + "/3"
                + " | retries " + retry.retries()
                + " | tokens left " + limiter.tokensLeft()
                + " | rate-limit rejections " + limiter.rejected()
                + " | metrics calls " + metrics.calls());
        System.out.println("     Every retry went back through the limiter, so retries burned");
        System.out.println("     quota and the third message never got a chance.");
        System.out.println();
    }

    private static void limiterOutside() {
        System.out.println("  2. rateLimit( retry( metrics( email ) ) )");
        EmailNotifier email = new EmailNotifier();
        MetricsNotifier metrics = new MetricsNotifier(email);
        RetryingNotifier retry = new RetryingNotifier(metrics, 3);
        RateLimitedNotifier limiter = new RateLimitedNotifier(retry, 4);

        int sent = attemptThree(limiter);

        System.out.println("     delivered " + sent + "/3"
                + " | retries " + retry.retries()
                + " | tokens left " + limiter.tokensLeft()
                + " | rate-limit rejections " + limiter.rejected()
                + " | metrics calls " + metrics.calls());
        System.out.println("     One token per logical send, retries underneath. Everything");
        System.out.println("     got through and there is quota to spare. This is the order");
        System.out.println("     you almost always want.");
        System.out.println();
    }

    private static void metricsOutside() {
        System.out.println("  3. metrics( retry( rateLimit( email ) ) )");
        EmailNotifier email = new EmailNotifier();
        RateLimitedNotifier limiter = new RateLimitedNotifier(email, 4);
        RetryingNotifier retry = new RetryingNotifier(limiter, 3);
        MetricsNotifier metrics = new MetricsNotifier(retry);

        int sent = attemptThree(metrics);

        System.out.println("     delivered " + sent + "/3"
                + " | retries " + retry.retries()
                + " | tokens left " + limiter.tokensLeft()
                + " | rate-limit rejections " + limiter.rejected()
                + " | metrics calls " + metrics.calls()
                + " | metrics failures " + metrics.failures());
        System.out.println("     Metrics now counts one call per message rather than per");
        System.out.println("     attempt, so your success rate measures user intent instead");
        System.out.println("     of network attempts. Different number, different meaning.");
        System.out.println();
    }

    private static void builtFromAList() {
        System.out.println("== The middleware variant: the same stack from a config list ==");
        EmailNotifier email = new EmailNotifier();

        // Innermost first. A servlet filter chain, an Express middleware stack
        // and a gRPC interceptor list are all exactly this, built by a framework
        // from a list instead of by you with nested constructors.
        List<UnaryOperator<Notifier>> layers = List.of(
                MetricsNotifier::new,
                RetryingNotifier::new,
                RateLimitedNotifier::new);

        Notifier stack = email;
        for (UnaryOperator<Notifier> layer : layers) {
            stack = layer.apply(stack);
        }

        int sent = attemptThree(stack);
        System.out.println("  delivered " + sent + "/3 through a stack assembled from a list.");
        System.out.println("  Reordering the pipeline is now reordering three strings in config,");
        System.out.println("  which is the same trick as the risk chain in lld/07.");
        System.out.println();
    }

    private static void theInheritanceAlternative() {
        System.out.println("== What you avoided ==");
        String[] features = {"retry", "rateLimit", "metrics", "encrypt"};
        System.out.println("  Four features as subclasses, one class per combination:");
        System.out.println("    " + (1 << features.length) + " classes, including gems like");
        System.out.println("    RetryingRateLimitedMetricsEncryptedNotifier");
        System.out.println("  Four features as decorators:");
        System.out.println("    " + features.length + " classes, and the combination is chosen");
        System.out.println("    at the call site rather than at compile time.");
        System.out.println();
        System.out.println("  That gap is the entire argument, and it widens exponentially.");
    }

    // ------------------------------------------------------------------

    private static int attemptThree(Notifier notifier) {
        int delivered = 0;
        for (int i = 1; i <= 3; i++) {
            try {
                notifier.send(new Message("customer-77", "notification " + i));
                delivered++;
            } catch (RuntimeException e) {
                System.out.println("     message " + i + " failed: " + e.getMessage());
            }
        }
        return delivered;
    }
}
