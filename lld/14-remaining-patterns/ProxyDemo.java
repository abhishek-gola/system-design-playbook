import java.util.HashMap;
import java.util.Map;

/**
 * Proxy: same shape as a Decorator, different intent.
 *
 * A decorator adds behaviour the caller wants. A proxy CONTROLS ACCESS to
 * something the caller cannot or should not reach directly — here, an expensive
 * report the caller would rather not pay for twice, plus a permission check the
 * caller is not allowed to skip.
 *
 * When asked how they differ, answer with intent. Structurally there is nothing
 * to tell apart.
 */
public class ProxyDemo {

    interface Report {
        String render(String user);
    }

    /** The real thing: slow and expensive to build. */
    static class RealReport implements Report {
        private int builds;

        @Override
        public String render(String user) {
            builds++;
            return "quarterly numbers (built " + builds + " time(s))";
        }
    }

    static class CachingSecureReportProxy implements Report {
        private final Report real;
        private final Map<String, String> cache = new HashMap<>();

        CachingSecureReportProxy(Report real) { this.real = real; }

        @Override
        public String render(String user) {
            if (!user.startsWith("finance-")) {
                return "denied — " + user + " may not read this";   // access control
            }
            return cache.computeIfAbsent(user, real::render);       // lazy + cached
        }
    }

    public static void show() {
        Report report = new CachingSecureReportProxy(new RealReport());
        System.out.println("    finance-anita: " + report.render("finance-anita"));
        System.out.println("    finance-anita: " + report.render("finance-anita")
                + "   <- served from cache, the real report never ran again");
        System.out.println("    intern-raj:    " + report.render("intern-raj"));
    }
}
