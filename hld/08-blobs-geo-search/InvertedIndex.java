import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One shard's index: term to a posting list of document ids. Everything else in
 * a search system is a consequence of this structure.
 *
 * Why it beats LIKE '%query%': a LIKE with a leading wildcard cannot use an
 * index, so every query is a full scan and every row pays the cost of a
 * substring match. The inverted index moves that work to write time, once per
 * document, and turns a query into a lookup plus a list intersection.
 *
 * What is skipped here, and worth naming in an interview because it is where
 * real systems spend their complexity:
 *
 *  - Segments. Lucene writes small immutable segments and merges them in the
 *    background rather than mutating one large index, which is what makes
 *    near-real-time indexing possible. A document is searchable once its
 *    segment is visible, not once it is merged. That is where the one-second
 *    refresh interval in Elasticsearch comes from, and it is why "how fresh are
 *    the results" has a real answer.
 *  - Deletions as tombstones, reclaimed at merge time, because you cannot
 *    cheaply remove an id from the middle of a compressed posting list.
 *  - Posting list compression, and skip lists so an intersection can jump ahead
 *    instead of walking every entry.
 */
public final class InvertedIndex {

    private final int shardId;
    private final Map<String, List<Integer>> postings = new TreeMap<>();
    private final Map<Integer, Doc> documents = new LinkedHashMap<>();
    private long postingEntriesScanned = 0;

    public InvertedIndex(int shardId) {
        this.shardId = shardId;
    }

    public void index(Doc doc) {
        documents.put(doc.id(), doc);
        // A set, because a term appearing five times in one document should
        // produce one posting, not five. Term frequency is worth keeping for
        // ranking, and would live in the posting alongside the id.
        for (String term : new LinkedHashSet<>(tokenise(doc.text()))) {
            postings.computeIfAbsent(term, k -> new ArrayList<>()).add(doc.id());
        }
    }

    /**
     * Ingest, tokenise, normalise. Lowercasing with Locale.ROOT rather than the
     * default is not pedantry: in a Turkish locale, "I".toLowerCase() is a
     * dotless i, so the same document indexes differently depending on where
     * the server happens to be running. Normalisation bugs of this shape are
     * miserable to find because the index and the query disagree silently.
     */
    public static List<String> tokenise(String text) {
        List<String> out = new ArrayList<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    /**
     * Stage one: intersect the posting lists and keep the best few by a cheap
     * score. No document text is read here, which is what makes it cheap.
     *
     * The intersection is AND, so a query term with no posting list means no
     * results at all from this shard. Real engines are more forgiving, but AND
     * is the right default to state and the right thing to relax deliberately.
     */
    public List<Candidate> retrieve(List<String> terms, int limit) {
        Map<Integer, Integer> termHits = new LinkedHashMap<>();
        for (String term : terms) {
            List<Integer> list = postings.get(term);
            if (list == null) {
                return new ArrayList<>();
            }
            postingEntriesScanned += list.size();
            for (int docId : list) {
                termHits.merge(docId, 1, Integer::sum);
            }
        }

        List<Candidate> matches = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : termHits.entrySet()) {
            if (entry.getValue() == terms.size()) {
                Doc doc = documents.get(entry.getKey());
                matches.add(new Candidate(doc, cheapScore(doc), shardId));
            }
        }
        matches.sort((a, b) -> {
            int byScore = Double.compare(b.cheapScore(), a.cheapScore());
            return byScore != 0 ? byScore : Integer.compare(a.doc().id(), b.doc().id());
        });

        if (matches.size() <= limit) {
            return matches;
        }
        return new ArrayList<>(matches.subList(0, limit));
    }

    /** A static quality prior. Log, so a post with a million likes does not simply win everything. */
    public static double cheapScore(Doc doc) {
        return Math.log(1.0 + doc.likes());
    }

    public int postingSize(String term) {
        List<Integer> list = postings.get(term);
        return list == null ? 0 : list.size();
    }

    public int shardId() {
        return shardId;
    }

    public int documentCount() {
        return documents.size();
    }

    public int termCount() {
        return postings.size();
    }

    public long postingEntriesScanned() {
        return postingEntriesScanned;
    }

    public void resetCounter() {
        postingEntriesScanned = 0;
    }

    public Set<String> terms() {
        return postings.keySet();
    }
}
