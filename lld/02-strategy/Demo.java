public class Demo {

    public static void main(String[] args) {
        ManualTicker clock = new ManualTicker(0);

        RateLimiter limiter = new RateLimiter(
                    new TokenBucketStrategy(100, 100, clock))            // generous default
                .register("/checkout", new TokenBucketStrategy(3, 1, clock))
                .register("/refund",   new SlidingWindowLogStrategy(3, 10_000, clock))
                .register("/search",   new FixedWindowCounterStrategy(3, 10_000, clock));

        System.out.println("== Token bucket: burst of 3, then refills at 1/sec ==");
        System.out.println("  " + limiter.strategyFor("/checkout").describe());
        fire(limiter, "/checkout", "user-42", 5);
        System.out.println("  ...wait 2 seconds...");
        clock.advance(2_000);
        fire(limiter, "/checkout", "user-42", 3);

        System.out.println();
        System.out.println("== Sliding window log: exact, no burst, nothing until the window slides ==");
        System.out.println("  " + limiter.strategyFor("/refund").describe());
        clock.advance(60_000);
        fire(limiter, "/refund", "user-42", 4);
        System.out.println("  ...wait 9 seconds (still inside the 10s window)...");
        clock.advance(9_000);
        fire(limiter, "/refund", "user-42", 1);
        System.out.println("  ...wait 2 more (the first three have now aged out)...");
        clock.advance(2_000);
        fire(limiter, "/refund", "user-42", 3);

        System.out.println();
        System.out.println("== Fixed window: the boundary flaw, on purpose ==");
        System.out.println("  " + limiter.strategyFor("/search").describe());
        clock.advance(120_000 - clock.millis() % 10_000);   // land on a window boundary
        System.out.println("  ...9.9 seconds into the window...");
        clock.advance(9_900);
        fire(limiter, "/search", "user-42", 3);
        System.out.println("  ...0.1 seconds later, a new window opens...");
        clock.advance(100);
        fire(limiter, "/search", "user-42", 3);
        System.out.println("  Six requests in 100ms against a limit of three per ten seconds.");
        System.out.println("  Say this out loud before the interviewer says it to you.");

        System.out.println();
        System.out.println("== The follow-up: change an algorithm at runtime, no deploy ==");
        System.out.println("  before: /search uses " + limiter.strategyFor("/search").describe());
        limiter.register("/search", new SlidingWindowLogStrategy(3, 10_000, clock));
        System.out.println("  after:  /search uses " + limiter.strategyFor("/search").describe());
        System.out.println("  One map write. No `if`, no subclass, no edit to RateLimiter.");
        System.out.println("  That is the whole reason the pattern is worth its weight.");

        System.out.println();
        System.out.println("== Keys are independent — user-99 is unaffected by user-42 ==");
        fire(limiter, "/checkout", "user-99", 2);
    }

    private static void fire(RateLimiter limiter, String route, String key, int n) {
        StringBuilder line = new StringBuilder("  " + route + " " + key + ": ");
        for (int i = 0; i < n; i++) {
            line.append(limiter.isAllowed(new Request(route, key)) ? "allow " : "BLOCK ");
        }
        System.out.println(line);
    }
}
