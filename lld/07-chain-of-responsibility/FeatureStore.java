import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Precomputed aggregates, read by the synchronous path and never computed by it.
 *
 * This is the LLD shadow of the feature store in hld/10-signature-design. The
 * whole reason a 100ms scoring budget is achievable is that "how many
 * transactions has this user made in the last hour" was answered by a stream
 * job minutes ago, and the request path only does a lookup.
 *
 * When the interviewer asks "wouldn't the velocity check be slow", this is the
 * answer, and it is a good one.
 */
public interface FeatureStore {

    int transactionsLastHour(String userId);

    boolean isKnownDevice(String userId, String deviceId);

    boolean isBlacklisted(String cardFingerprint);

    // ------------------------------------------------------------------

    class InMemory implements FeatureStore {
        private final Map<String, Integer> velocity = new HashMap<>();
        private final Map<String, Set<String>> devices = new HashMap<>();
        private final Set<String> blacklist = new HashSet<>();

        public InMemory withVelocity(String userId, int count) {
            velocity.put(userId, count);
            return this;
        }

        public InMemory withKnownDevice(String userId, String deviceId) {
            devices.computeIfAbsent(userId, k -> new HashSet<>()).add(deviceId);
            return this;
        }

        public InMemory withBlacklistedCard(String fingerprint) {
            blacklist.add(fingerprint);
            return this;
        }

        @Override public int transactionsLastHour(String userId) {
            return velocity.getOrDefault(userId, 0);
        }

        @Override public boolean isKnownDevice(String userId, String deviceId) {
            return devices.getOrDefault(userId, Set.of()).contains(deviceId);
        }

        @Override public boolean isBlacklisted(String fingerprint) {
            return blacklist.contains(fingerprint);
        }
    }
}
