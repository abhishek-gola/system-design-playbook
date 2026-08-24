import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The consistency gap between a metadata database and a blob store, and the
 * state machine that closes it.
 *
 * The core rule of this pattern is that bytes never pass through your
 * application. Your server issues a presigned URL, the client uploads straight
 * to object storage, and something afterwards updates the metadata. That is
 * excellent for throughput and it creates a problem you have to answer for:
 * you now have two systems that can disagree, with no transaction spanning
 * them.
 *
 * Every failure mode is a different point at which the client stops
 * cooperating:
 *
 *   presign, then nothing            metadata row with no bytes behind it
 *   presign, upload, then crash      bytes uploaded, metadata still PENDING
 *   upload with a stale or reused
 *   URL, no metadata at all          bytes with nothing referencing them
 *
 * The answer is a two-state machine plus a sweeper that runs in both
 * directions. Write metadata as PENDING before handing out the URL, flip it to
 * COMMITTED only once the upload is confirmed, and periodically clean up
 * anything that has been PENDING longer than a sane upload could take, along
 * with any object nothing points at.
 *
 * The reason to write the PENDING row *first*, rather than only writing
 * metadata on the callback, is that it gives the sweeper something to find. If
 * you write no metadata until the client confirms, an object uploaded by a
 * client that then died is invisible, and you pay S3 for it forever.
 */
public final class BlobMetadataStore {

    public enum State {
        PENDING, COMMITTED
    }

    public record Entry(String fileId, String blobKey, State state, long createdAtMs) {

        public Entry asCommitted() {
            return new Entry(fileId, blobKey, State.COMMITTED, createdAtMs);
        }
    }

    private final Map<String, Entry> metadata = new LinkedHashMap<>();

    /** Stands in for S3. Membership is all we need; the bytes themselves are not the point here. */
    private final Set<String> objectStore = new LinkedHashSet<>();

    /** Step one. A row appears before a single byte moves. */
    public String presign(String fileId, String blobKey, long nowMs) {
        metadata.put(fileId, new Entry(fileId, blobKey, State.PENDING, nowMs));
        return "https://blobs.example/" + blobKey + "?expires=" + (nowMs + 900_000L) + "&sig=...";
    }

    /** Step two. The client PUTs directly to object storage; none of your servers are involved. */
    public void clientUploadsDirectly(String blobKey) {
        objectStore.add(blobKey);
    }

    /**
     * Step three, driven either by a client callback or by an S3 event
     * notification. The S3 event is the more robust of the two, because it does
     * not depend on the client surviving long enough to make a second call.
     *
     * Note that the commit verifies the bytes are actually there. A callback is
     * a claim, not a fact, and a client that calls back without uploading would
     * otherwise leave you with a COMMITTED row pointing at nothing — which is
     * the worst of the states, because the sweeper will not touch it and a
     * reader will get a 404.
     */
    public String commit(String fileId) {
        Entry entry = metadata.get(fileId);
        if (entry == null) {
            return "rejected: no metadata row for " + fileId;
        }
        if (!objectStore.contains(entry.blobKey())) {
            return "rejected: " + fileId + " has no bytes in the blob store";
        }
        metadata.put(fileId, entry.asCommitted());
        return "committed: " + fileId;
    }

    /** Runs on a schedule. Both directions, because the disagreement can point either way. */
    public List<String> sweep(long nowMs, long pendingTtlMs) {
        List<String> actions = new ArrayList<>();

        // Direction one: metadata that has been PENDING longer than any real
        // upload would take. The TTL wants to be generously longer than your
        // largest plausible upload over a bad connection, because sweeping a
        // slow-but-live upload is a bug your users will find before you do.
        List<String> stale = new ArrayList<>();
        for (Entry entry : metadata.values()) {
            if (entry.state() == State.PENDING && nowMs - entry.createdAtMs() >= pendingTtlMs) {
                stale.add(entry.fileId());
            }
        }
        for (String fileId : stale) {
            Entry entry = metadata.remove(fileId);
            boolean hadBytes = objectStore.remove(entry.blobKey());
            actions.add("dropped stale PENDING " + fileId
                    + (hadBytes ? ", and deleted the bytes it had already uploaded" : " (no bytes were ever uploaded)"));
        }

        // Direction two: objects nothing references. Note this must run after
        // the first pass, and note the ordering risk in production — an object
        // uploaded a moment ago whose PENDING row has not yet replicated to the
        // reader you are sweeping from would look like an orphan. Give orphan
        // deletion its own age threshold rather than deleting on sight.
        Set<String> referenced = new LinkedHashSet<>();
        for (Entry entry : metadata.values()) {
            referenced.add(entry.blobKey());
        }
        List<String> orphans = new ArrayList<>();
        for (String key : objectStore) {
            if (!referenced.contains(key)) {
                orphans.add(key);
            }
        }
        for (String key : orphans) {
            objectStore.remove(key);
            actions.add("deleted orphan object " + key + ", which no metadata row pointed at");
        }

        return actions;
    }

    public Map<String, Entry> metadata() {
        return metadata;
    }

    public Set<String> objectStore() {
        return objectStore;
    }
}
