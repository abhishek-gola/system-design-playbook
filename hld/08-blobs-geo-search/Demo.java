import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Three narrower patterns, one demo.
 *
 * Run it with ./run.sh hld/08-blobs-geo-search
 *
 * Large blobs, proximity and search do not have much to do with each other.
 * They are grouped because each shows up in a handful of specific problems and
 * each has exactly one non-obvious core idea, and it is the non-obvious idea
 * that this demo tries to make impossible to forget:
 *
 *   blobs   content-defined chunking survives an insertion; fixed-size does not
 *   geo     a single-cell lookup silently misses things across a boundary
 *   search  shard by document, and rank in two stages
 */
public final class Demo {

    public static void main(String[] args) {
        largeBlobs();
        proximity();
        searchAndRanking();

        System.out.println();
        System.out.println("Three patterns, three ideas worth carrying: cut on content, search nine");
        System.out.println("cells, and never score everything.");
    }

    // ==================================================================
    // A. Large blobs
    // ==================================================================

    private static void largeBlobs() {
        section("A1. Chunking: fixed size versus content defined");

        System.out.println("The core rule first, because everything else follows from it: bytes never");
        System.out.println("pass through your application. The server issues a presigned URL, the client");
        System.out.println("uploads straight to object storage, and either an S3 event or a client");
        System.out.println("callback updates the metadata. Downloads go out through a CDN, with signed");
        System.out.println("URLs when access control matters.");
        System.out.println();
        System.out.println("Chunking is what makes uploads resumable and deduplicated. How you cut");
        System.out.println("decides whether the dedup survives an edit.");
        System.out.println();

        byte[] original = syntheticFile(4096);
        byte[] edited = withBytesPrependedAtTheStart(original, 10);

        System.out.println(fmt("Original file %d bytes. The user then inserts %d bytes at the very start",
                original.length, edited.length - original.length));
        System.out.println("and re-syncs. Nothing else about the file changes.");
        System.out.println();

        List<Chunker.Chunk> fixedBefore = Chunker.fixedSize(original, 64);
        List<Chunker.Chunk> fixedAfter = Chunker.fixedSize(edited, 64);
        int fixedReused = reusedChunks(fixedBefore, fixedAfter);

        List<Chunker.Chunk> cdcBefore = Chunker.contentDefined(original, 16, 0x3F, 32, 256);
        List<Chunker.Chunk> cdcAfter = Chunker.contentDefined(edited, 16, 0x3F, 32, 256);
        int cdcReused = reusedChunks(cdcBefore, cdcAfter);

        System.out.println("   scheme                    chunks before   chunks after   reused   re-uploaded");
        System.out.println(fmt("   fixed size, 64 bytes      %-15d %-14d %-8d %d",
                fixedBefore.size(), fixedAfter.size(), fixedReused, fixedAfter.size() - fixedReused));
        System.out.println(fmt("   content defined, ~64 avg  %-15d %-14d %-8d %d",
                cdcBefore.size(), cdcAfter.size(), cdcReused, cdcAfter.size() - cdcReused));
        System.out.println();
        System.out.println(fmt("Fixed-size chunking re-uploads %.0f%% of the file for a %d byte insert,",
                100.0 * (fixedAfter.size() - fixedReused) / fixedAfter.size(), edited.length - original.length));
        System.out.println("because every boundary after the insertion point shifted by ten bytes and");
        System.out.println("every chunk hash changed with it.");
        System.out.println();
        System.out.println(fmt("Content-defined chunking re-uploads %d chunks. Boundaries are decided by",
                cdcAfter.size() - cdcReused));
        System.out.println("a hash of the bytes around them rather than by position, so once the scan");
        System.out.println("gets past the inserted region the cut points land in exactly the same places");
        System.out.println("as before and the rest of the file is recognised as already held.");
        System.out.println();
        System.out.println("The first few boundaries in each file, as offsets:");
        System.out.println(fmt("   original : %s", firstOffsets(cdcBefore, 8)));
        System.out.println(fmt("   edited   : %s", firstOffsets(cdcAfter, 8)));
        System.out.println("The edited offsets converge on original-plus-ten within a chunk or two and");
        System.out.println("then stay there. That resynchronisation is the entire trick: a chunking");
        System.out.println("scheme that recovers alignment on its own after a disturbance.");
        System.out.println();
        System.out.println("The cost you are paying: a real chunk is megabytes rather than 64 bytes, the");
        System.out.println("rolling hash costs CPU on every byte of every upload, and the chunk index");
        System.out.println("becomes a large hash-to-location table you have to keep and garbage collect.");
        System.out.println("Fixed-size chunking is genuinely the right answer for write-once media that");
        System.out.println("is never edited, which is most of what Instagram stores.");

        // ------------------------------------------------------------------
        section("A2. The consistency gap: PENDING, COMMITTED, and a sweeper");

        System.out.println("Metadata lives in a database, bytes live in a blob store, and no transaction");
        System.out.println("spans the two. Every failure mode below is just a different point at which");
        System.out.println("the client stops cooperating.");
        System.out.println();

        BlobMetadataStore store = new BlobMetadataStore();
        long t0 = 0L;

        store.presign("file-happy", "blob-happy", t0);
        store.clientUploadsDirectly("blob-happy");
        System.out.println("   " + store.commit("file-happy") + "   (the path everything is designed for)");

        store.presign("file-crashed", "blob-crashed", t0);
        store.clientUploadsDirectly("blob-crashed");
        System.out.println("   file-crashed: bytes uploaded, then the client died before calling back");

        store.presign("file-abandoned", "blob-abandoned", t0);
        System.out.println("   file-abandoned: URL issued, the user changed their mind, nothing uploaded");

        store.presign("file-liar", "blob-liar", t0);
        System.out.println("   " + store.commit("file-liar") + "   (a callback is a claim, not a fact)");

        store.clientUploadsDirectly("blob-from-nowhere");
        System.out.println("   blob-from-nowhere: bytes uploaded on a reused URL, no metadata row at all");

        System.out.println();
        System.out.println("State before the sweeper runs:");
        printBlobState(store);

        long later = 1_800_000L;
        List<String> actions = store.sweep(later, 900_000L);
        System.out.println();
        System.out.println(fmt("Sweeper at t+%d minutes, PENDING TTL %d minutes:", later / 60_000, 900_000L / 60_000));
        for (String action : actions) {
            System.out.println("   " + action);
        }

        System.out.println();
        System.out.println("State after:");
        printBlobState(store);

        System.out.println();
        System.out.println("One COMMITTED row with its bytes intact, and nothing else left behind. The");
        System.out.println("detail worth defending: the PENDING row is written before the URL is handed");
        System.out.println("out, not on the callback. Write metadata only on confirmation and an upload");
        System.out.println("from a client that then died is invisible to you, and you pay to store it");
        System.out.println("forever. The row exists so that the sweeper has something to find.");
    }

    // ==================================================================
    // B. Proximity and geospatial
    // ==================================================================

    private static void proximity() {
        section("B1. Geohash: encoding a box as a string");

        int precision = 5;

        // Deliberately put the query point close to the southern edge of its own
        // cell. The boundary case is the whole lesson, so it should not be left
        // to luck about where a city centre happens to fall.
        double nominalLat = 12.9716;
        double nominalLon = 77.5946;
        GeoHash.Box nominalBox = GeoHash.decode(GeoHash.encode(nominalLat, nominalLon, precision));
        double queryLat = nominalBox.minLat() + 0.001;
        double queryLon = nominalBox.centreLon();

        String queryCell = GeoHash.encode(queryLat, queryLon, precision);
        GeoHash.Box box = GeoHash.decode(queryCell);

        System.out.println(fmt("Query point %.5f, %.5f", queryLat, queryLon));
        System.out.println(fmt("Geohash at precision 8: %s   (street level)", GeoHash.encode(queryLat, queryLon, 8)));
        System.out.println(fmt("Geohash at precision 5: %s   (the cell we will index on)", queryCell));
        System.out.println();
        System.out.println("Decoding gives a box back, never a point, because a geohash is an area:");
        System.out.println(fmt("   latitude  %.5f to %.5f  (%.0f m tall)",
                box.minLat(), box.maxLat(),
                GeoHash.haversineMetres(box.minLat(), box.centreLon(), box.maxLat(), box.centreLon())));
        System.out.println(fmt("   longitude %.5f to %.5f  (%.0f m wide)",
                box.minLon(), box.maxLon(),
                GeoHash.haversineMetres(box.centreLat(), box.minLon(), box.centreLat(), box.maxLon())));
        System.out.println();
        System.out.println("The eight neighbours:");
        System.out.println("   " + String.join("  ", GeoHash.neighbours(queryCell)));
        System.out.println();
        System.out.println(fmt("The query point sits %.0f m from the southern edge of its own cell, which",
                GeoHash.haversineMetres(queryLat, queryLon, box.minLat(), queryLon)));
        System.out.println("is not unusual. Somebody is always near an edge.");

        // ------------------------------------------------------------------
        section("B2. Why a single-cell lookup is wrong");

        GeoIndex index = new GeoIndex(precision);
        List<Place> places = gridOfPlaces(queryLat, queryLon);
        for (Place place : places) {
            index.add(place);
        }

        double radius = 2000;
        List<GeoIndex.Hit> naive = index.naiveSingleCell(queryLat, queryLon, radius);
        List<GeoIndex.Hit> correct = index.nearby(queryLat, queryLon, radius);

        System.out.println(fmt("%d places indexed across %d cells. Searching within %.0f m.",
                places.size(), index.cellCount(), radius));
        System.out.println();
        System.out.println(fmt("   single cell only : scanned %d candidates, returned %d results",
                index.candidatesScannedByNaive(queryLat, queryLon), naive.size()));
        System.out.println(fmt("   nine cells       : scanned %d candidates, returned %d results",
                index.candidatesScannedByNineCell(queryLat, queryLon), correct.size()));

        Set<String> foundByNaive = new HashSet<>();
        for (GeoIndex.Hit hit : naive) {
            foundByNaive.add(hit.place().name());
        }
        List<GeoIndex.Hit> missed = new ArrayList<>();
        for (GeoIndex.Hit hit : correct) {
            if (!foundByNaive.contains(hit.place().name())) {
                missed.add(hit);
            }
        }

        System.out.println();
        System.out.println(fmt("The single-cell search misses %d places that are genuinely within range.",
                missed.size()));
        if (!missed.isEmpty()) {
            System.out.println("The nearest few, with the cell each one landed in:");
            System.out.println();
            for (int i = 0; i < Math.min(4, missed.size()); i++) {
                GeoIndex.Hit hit = missed.get(i);
                System.out.println(fmt("   %-12s %6.0f m away, in cell %s, not %s",
                        hit.place().name(), hit.metres(),
                        index.cellFor(hit.place().lat(), hit.place().lon()), queryCell));
            }
            System.out.println();
            System.out.println(fmt("The closest is %.0f m from the query point. It is invisible to a",
                    missed.get(0).metres()));
            System.out.println("single-cell search because the cell boundary runs between them, and the");
            System.out.println("high bits of the geohash flip there. This bug does not throw, does not");
            System.out.println("log, and looks like a slightly thin result set. It is why every real");
            System.out.println("proximity query searches nine cells and then filters by exact distance.");
        }

        System.out.println();
        System.out.println("The other structures, and when to bother:");
        System.out.println();
        System.out.println("   quadtree   subdivides where density is high, so it handles a country with");
        System.out.println("              one enormous city far better than a uniform grid. Harder to");
        System.out.println("              distribute, because the tree shape itself is shared state.");
        System.out.println("   S2 / H3    production-grade cell systems. H3's hexagons have uniform");
        System.out.println("              neighbour distances, which is what you want for delivery zones");
        System.out.println("              and coverage maps, where a rectangle's diagonal neighbours");
        System.out.println("              being further away than its edge neighbours actually matters.");
        System.out.println();
        System.out.println("Geohash is still the right default answer: it is a string, so any database");
        System.out.println("indexes it and any key-value store shards on it, and you can explain it in a");
        System.out.println("minute. Reach for the others when you can say why.");
        System.out.println();
        System.out.println("And the thing to say unprompted: this whole design assumes the places do not");
        System.out.println("move. Yelp is a read problem. Uber drivers pinging every four seconds are a");
        System.out.println("write problem with a geospatial index attached — keep current positions in");
        System.out.println("memory or Redis with a short TTL, and do not persist every ping.");
    }

    // ==================================================================
    // C. Search and ranking
    // ==================================================================

    private static void searchAndRanking() {
        section("C1. Sharding by document versus by term");

        List<Doc> corpus = syntheticCorpus(2000);
        SearchCluster cluster = new SearchCluster(4);
        cluster.index(corpus);

        System.out.println(fmt("%d posts, %d terms, spread over %d shards by document id.",
                corpus.size(), cluster.wholeCorpus().termCount(), cluster.shardCount()));
        System.out.println();
        System.out.println("Posting list sizes across the whole corpus, which is the thing that decides");
        System.out.println("whether term sharding can work:");
        System.out.println();
        for (String term : new String[]{"the", "coffee", "traffic", "biryani", "eclipse"}) {
            int size = cluster.wholeCorpus().postingSize(term);
            System.out.println(fmt("   %-10s %-6d %s", term, size, bar(size / 40)));
        }
        System.out.println();
        System.out.println("That distribution is the point. Term frequencies are Zipf shaped, always.");
        System.out.println();

        List<List<String>> workload = syntheticWorkload(200);
        Map<Integer, Long> byDocument = cluster.documentShardedLoad(workload, 50);
        Map<Integer, Long> byTerm = cluster.termShardedLoad(workload);

        System.out.println(fmt("Posting entries each shard walks for a %d query workload:", workload.size()));
        System.out.println();
        System.out.println("   shard   sharded by document        sharded by term");
        for (int shard = 0; shard < cluster.shardCount(); shard++) {
            long doc = byDocument.getOrDefault(shard, 0L);
            long term = byTerm.getOrDefault(shard, 0L);
            System.out.println(fmt("   %-7d %-8d %-16s %-8d %s",
                    shard, doc, bar((int) (doc / 4000)), term, bar((int) (term / 4000))));
        }
        System.out.println();
        System.out.println(fmt("   by document: busiest shard does %.1fx the work of the quietest",
                skew(byDocument)));
        System.out.println(fmt("   by term    : busiest shard does %.1fx the work of the quietest",
                skew(byTerm)));
        System.out.println();
        System.out.println("Document sharding spreads the work almost perfectly, because a shard's load");
        System.out.println("depends on how many of its own documents match rather than on which words");
        System.out.println("were used. Every shard holds a complete index over its own slice, searches");
        System.out.println("locally, and returns its top results to a coordinator that merges.");
        System.out.println();
        System.out.println("Term sharding piles the work onto whichever shard owns the common words, and");
        System.out.println("adding shards does not help because a hot term cannot be split. It has a");
        System.out.println("second problem this measurement does not show: intersecting two terms that");
        System.out.println("live on different machines means shipping posting lists across the network");
        System.out.println("on the request path.");
        System.out.println();
        System.out.println("The cost of document sharding, stated honestly so you get there first: every");
        System.out.println("query fans out to every shard, so your tail latency is the slowest shard's");
        System.out.println("tail latency, and that gets worse as you add shards. The mitigations are");
        System.out.println("hedged requests and being willing to return results from the shards that");
        System.out.println("answered in time.");

        // ------------------------------------------------------------------
        section("C2. Ranking in two stages");

        List<String> query = List.of("the", "coffee");

        SearchCluster.Scorer twoStageScorer = new SearchCluster.Scorer();
        List<Candidate> shortlist = cluster.retrieve(query, 50);
        List<SearchCluster.Ranked> twoStage = SearchCluster.rank(shortlist, query, twoStageScorer, 5);

        SearchCluster.Scorer everythingScorer = new SearchCluster.Scorer();
        List<Candidate> everything = cluster.retrieveEverything(query);
        List<SearchCluster.Ranked> scoreEverything = SearchCluster.rank(everything, query, everythingScorer, 5);

        System.out.println(fmt("Query: %s", String.join(" ", query)));
        System.out.println(fmt("%d documents in the corpus match both terms.", everything.size()));
        System.out.println();
        System.out.println("   approach                        expensive scorings   top result");
        System.out.println(fmt("   score everything that matches   %-20d doc %d",
                everythingScorer.calls(), scoreEverything.get(0).doc().id()));
        System.out.println(fmt("   two stage, top 50 per shard     %-20d doc %d",
                twoStageScorer.calls(), twoStage.get(0).doc().id()));
        System.out.println();

        int agree = 0;
        for (int i = 0; i < Math.min(twoStage.size(), scoreEverything.size()); i++) {
            if (twoStage.get(i).doc().id() == scoreEverything.get(i).doc().id()) {
                agree++;
            }
        }
        System.out.println(fmt("The top five agree on %d of 5 positions, for %.0f%% of the scoring cost.",
                agree, 100.0 * twoStageScorer.calls() / everythingScorer.calls()));
        System.out.println();
        System.out.println("   rank  doc    shard   score   author");
        for (int i = 0; i < twoStage.size(); i++) {
            SearchCluster.Ranked r = twoStage.get(i);
            System.out.println(fmt("   %-5d %-6d %-7d %-7.3f %s",
                    i + 1, r.doc().id(), r.shardId(), r.score(), r.doc().author()));
        }
        System.out.println();
        System.out.println("Stage one is cheap because it never opens a document: it intersects posting");
        System.out.println("lists and ranks by a static signal that was known at index time. Stage two");
        System.out.println("reads the body, and in production is a learned model that costs milliseconds");
        System.out.println("per document. Running it on everything that matched is the mistake, and at");
        System.out.println("real corpus sizes it is not a slow answer, it is no answer.");
        System.out.println();
        System.out.println("What you give up is recall: a document that stage one ranked poorly never");
        System.out.println("reaches the scorer that would have loved it. Widening the shortlist buys");
        System.out.println("recall and costs latency, and that dial is the whole of ranking engineering.");

        // ------------------------------------------------------------------
        section("C3. Autocomplete is a different animal");

        Autocomplete autocomplete = new Autocomplete(5);
        for (String[] entry : queryLog()) {
            autocomplete.addQueryLogEntry(entry[0], Long.parseLong(entry[1]));
        }
        autocomplete.build();

        System.out.println(fmt("%d queries from the log, built offline into a trie of %d nodes with the",
                autocomplete.queryLogSize(), autocomplete.nodeCount()));
        System.out.println("top 5 completions precomputed at every node.");
        System.out.println();

        for (String prefix : new String[]{"c", "co", "cof"}) {
            List<Autocomplete.Suggestion> suggestions = autocomplete.suggest(prefix);
            autocomplete.scanWholeLog(prefix);
            System.out.println(fmt("   \"%s\"", prefix));
            for (Autocomplete.Suggestion suggestion : suggestions) {
                System.out.println(fmt("        %-28s %d", suggestion.query(), suggestion.weight()));
            }
            System.out.println(fmt("        trie: %d node hops   |   scanning the log: %d comparisons",
                    autocomplete.lastLookupSteps(), autocomplete.lastScanComparisons()));
            System.out.println();
        }

        System.out.println("The trie lookup costs one hop per character typed and nothing else. It does");
        System.out.println("not grow with the number of known queries, which is the property that makes");
        System.out.println("a single-digit millisecond budget achievable on every keystroke.");
        System.out.println();
        System.out.println("The parts that make it a system rather than a data structure: weights come");
        System.out.println("from aggregated query logs, the whole trie is rebuilt offline and swapped in");
        System.out.println("rather than updated in place, and anything trending gets a small separate");
        System.out.println("trie layered over the big stable one. Sharding is by prefix, so everything");
        System.out.println("under \"lo\" lives together and a request routes on its first few characters.");
        System.out.println();
        System.out.println("Say the volume figure out loud: autocomplete sees roughly ten times the");
        System.out.println("traffic of the search it feeds, because it fires on every keystroke. That is");
        System.out.println("why it gets its own service, its own budget and its own data structure.");
    }

    // ==================================================================
    // synthetic data
    // ==================================================================

    private static byte[] syntheticFile(int length) {
        byte[] data = new byte[length];
        new Random(7).nextBytes(data);
        return data;
    }

    private static byte[] withBytesPrependedAtTheStart(byte[] original, int count) {
        byte[] out = new byte[original.length + count];
        Random rnd = new Random(99);
        for (int i = 0; i < count; i++) {
            out[i] = (byte) rnd.nextInt(256);
        }
        System.arraycopy(original, 0, out, count, original.length);
        return out;
    }

    private static int reusedChunks(List<Chunker.Chunk> before, List<Chunker.Chunk> after) {
        Set<String> held = new HashSet<>();
        for (Chunker.Chunk chunk : before) {
            held.add(chunk.hash());
        }
        int reused = 0;
        for (Chunker.Chunk chunk : after) {
            if (held.contains(chunk.hash())) {
                reused++;
            }
        }
        return reused;
    }

    private static String firstOffsets(List<Chunker.Chunk> chunks, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(count, chunks.size()); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(chunks.get(i).offset());
        }
        return sb.toString();
    }

    private static void printBlobState(BlobMetadataStore store) {
        System.out.println("   metadata rows:");
        if (store.metadata().isEmpty()) {
            System.out.println("      (none)");
        }
        for (BlobMetadataStore.Entry entry : store.metadata().values()) {
            System.out.println(fmt("      %-16s %-10s -> %s", entry.fileId(), entry.state(), entry.blobKey()));
        }
        System.out.println("   objects in the blob store:");
        if (store.objectStore().isEmpty()) {
            System.out.println("      (none)");
        }
        for (String key : store.objectStore()) {
            System.out.println("      " + key);
        }
    }

    /** A grid around the query point, deliberately wide enough to cross a cell boundary. */
    private static List<Place> gridOfPlaces(double centreLat, double centreLon) {
        List<Place> places = new ArrayList<>();
        int n = 0;
        for (int row = -3; row <= 3; row++) {
            for (int col = -3; col <= 3; col++) {
                double lat = centreLat + row * 0.005;
                double lon = centreLon + col * 0.005;
                places.add(new Place("cafe-" + (++n), lat, lon));
            }
        }
        return places;
    }

    private static final String[] COMMON = {"the", "a", "and"};
    private static final String[] MID = {"cricket", "match", "score", "coffee", "traffic", "monsoon", "office", "weekend"};
    private static final String[] RARE = {"biryani", "quiz", "marathon", "eclipse", "sourdough", "podcast"};

    private static List<Doc> syntheticCorpus(int size) {
        Random rnd = new Random(3);
        String[] authors = {"asha", "ben", "chandra", "dev", "eve"};
        List<Doc> corpus = new ArrayList<>();
        for (int id = 0; id < size; id++) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                text.append(COMMON[rnd.nextInt(COMMON.length)]).append(' ');
                text.append(MID[rnd.nextInt(MID.length)]).append(' ');
            }
            if (rnd.nextInt(10) == 0) {
                text.append(RARE[rnd.nextInt(RARE.length)]).append(' ');
            }
            corpus.add(new Doc(id, authors[rnd.nextInt(authors.length)], text.toString().trim(), rnd.nextInt(5000)));
        }
        return corpus;
    }

    private static List<List<String>> syntheticWorkload(int queries) {
        Random rnd = new Random(21);
        List<List<String>> workload = new ArrayList<>();
        for (int i = 0; i < queries; i++) {
            // Real query terms are Zipf shaped too: most queries include a very
            // common word. Squaring the draw biases hard towards index zero.
            double u = rnd.nextDouble();
            String common = COMMON[Math.min((int) (u * u * COMMON.length), COMMON.length - 1)];
            String mid = MID[rnd.nextInt(MID.length)];
            workload.add(List.of(common, mid));
        }
        return workload;
    }

    private static String[][] queryLog() {
        return new String[][]{
                {"coffee near me", "98000"},
                {"coffee shops open now", "54000"},
                {"coffee machine descaling", "12000"},
                {"coffee beans delivery", "9000"},
                {"coffee table", "31000"},
                {"cold brew recipe", "22000"},
                {"cold weather kit", "3000"},
                {"cricket score", "120000"},
                {"cricket world cup final", "76000"},
                {"cinema tickets", "41000"},
                {"commute times", "6000"},
                {"contact lens offers", "4000"},
                {"corner sofa", "8000"},
                {"council tax bands", "15000"},
                {"train times", "88000"},
                {"traffic on the ring road", "27000"},
        };
    }

    // ==================================================================
    // helpers
    // ==================================================================

    private static double skew(Map<Integer, Long> load) {
        long max = 0;
        long min = Long.MAX_VALUE;
        for (long value : load.values()) {
            max = Math.max(max, value);
            min = Math.min(min, value);
        }
        if (min == 0) {
            return max == 0 ? 1.0 : Double.POSITIVE_INFINITY;
        }
        return (double) max / min;
    }

    private static String bar(int n) {
        return "#".repeat(Math.max(0, Math.min(n, 30)));
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println(title);
        System.out.println("=".repeat(78));
        System.out.println();
    }

    private static String fmt(String format, Object... args) {
        return String.format(Locale.UK, format, args);
    }
}
