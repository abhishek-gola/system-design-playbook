import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sharding, merging and two-stage ranking.
 *
 * The single decision this class exists to make visible: shard by document, not
 * by term.
 *
 * Sharded by document, each shard holds a complete index over its own slice of
 * the corpus. A query goes to every shard, each one searches locally and
 * returns its own top results, and a coordinator merges them. Every shard does
 * a small, similar amount of work, adding capacity means adding shards, and a
 * shard failing degrades results rather than breaking the query.
 *
 * Sharded by term, each shard owns a set of terms and the whole posting list
 * for each. It sounds more efficient — a two-term query only touches two shards
 * instead of all of them — and it is a trap. Term frequencies are Zipf
 * distributed, so whichever shard owns the common words handles most queries
 * and holds most of the data, and no amount of adding shards helps because the
 * hot term cannot be split. Worse, an intersection now needs posting lists from
 * different machines to meet somewhere, so you are shipping large lists across
 * the network on the request path.
 *
 * The demo measures both, so the hot shard is a printed number rather than an
 * assertion.
 */
public final class SearchCluster {

    /** Stage two. Expensive because it reads the document body; a learned ranking model in production. */
    public static final class Scorer {

        private long calls = 0;

        public double score(Doc doc, List<String> terms) {
            calls++;
            List<String> tokens = InvertedIndex.tokenise(doc.text());
            double score = 0;
            for (String term : terms) {
                int firstPosition = tokens.indexOf(term);
                if (firstPosition >= 0) {
                    // Earlier mentions count for more. A real scorer would use
                    // BM25 over term frequency and document length, plus
                    // proximity between the query terms, plus whatever the
                    // model has learned. The shape is what matters: it needs
                    // the document, which is why you cannot afford to run it on
                    // everything.
                    score += 1.0 / (1.0 + firstPosition);
                }
            }
            return score + 0.1 * InvertedIndex.cheapScore(doc);
        }

        public long calls() {
            return calls;
        }
    }

    public record Ranked(Doc doc, double score, int shardId) {
    }

    private final List<InvertedIndex> shards = new ArrayList<>();
    private final InvertedIndex wholeCorpus = new InvertedIndex(-1);

    public SearchCluster(int shardCount) {
        for (int i = 0; i < shardCount; i++) {
            shards.add(new InvertedIndex(i));
        }
    }

    public void index(List<Doc> corpus) {
        for (Doc doc : corpus) {
            // Document id decides the shard. Any even spread works; using the id
            // rather than a content hash means a document always lands on the
            // same shard, which matters for updates and deletes.
            shards.get(Math.floorMod(doc.id(), shards.size())).index(doc);
            wholeCorpus.index(doc);
        }
    }

    /** Scatter to every shard, take each shard's best, merge. */
    public List<Candidate> retrieve(List<String> terms, int perShard) {
        List<Candidate> merged = new ArrayList<>();
        for (InvertedIndex shard : shards) {
            merged.addAll(shard.retrieve(terms, perShard));
        }
        merged.sort((a, b) -> {
            int byScore = Double.compare(b.cheapScore(), a.cheapScore());
            return byScore != 0 ? byScore : Integer.compare(a.doc().id(), b.doc().id());
        });
        return merged;
    }

    /** Everything that matches, unranked and untrimmed. Only used to show what scoring the lot would cost. */
    public List<Candidate> retrieveEverything(List<String> terms) {
        return wholeCorpus.retrieve(terms, Integer.MAX_VALUE);
    }

    public static List<Ranked> rank(List<Candidate> candidates, List<String> terms, Scorer scorer, int limit) {
        List<Ranked> ranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ranked.add(new Ranked(candidate.doc(), scorer.score(candidate.doc(), terms), candidate.shardId()));
        }
        ranked.sort((a, b) -> {
            int byScore = Double.compare(b.score(), a.score());
            return byScore != 0 ? byScore : Integer.compare(a.doc().id(), b.doc().id());
        });
        if (ranked.size() <= limit) {
            return ranked;
        }
        return new ArrayList<>(ranked.subList(0, limit));
    }

    /** Posting entries each shard has to walk for a workload, under document sharding. */
    public Map<Integer, Long> documentShardedLoad(List<List<String>> queries, int perShard) {
        for (InvertedIndex shard : shards) {
            shard.resetCounter();
        }
        for (List<String> query : queries) {
            retrieve(query, perShard);
        }
        Map<Integer, Long> load = new TreeMap<>();
        for (InvertedIndex shard : shards) {
            load.put(shard.shardId(), shard.postingEntriesScanned());
        }
        return load;
    }

    /**
     * The same workload under term sharding, measured rather than built. A term
     * lives on one shard, and that shard walks the whole global posting list for
     * it, so the work is the corpus-wide posting size and it all lands in one
     * place.
     */
    public Map<Integer, Long> termShardedLoad(List<List<String>> queries) {
        Map<Integer, Long> load = new TreeMap<>();
        for (int i = 0; i < shards.size(); i++) {
            load.put(i, 0L);
        }
        for (List<String> query : queries) {
            for (String term : query) {
                int shard = Math.floorMod(term.hashCode(), shards.size());
                load.merge(shard, (long) wholeCorpus.postingSize(term), Long::sum);
            }
        }
        return load;
    }

    public int shardCount() {
        return shards.size();
    }

    public InvertedIndex wholeCorpus() {
        return wholeCorpus;
    }

    public List<InvertedIndex> shards() {
        return shards;
    }
}
