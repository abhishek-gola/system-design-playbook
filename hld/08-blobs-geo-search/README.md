# Large blobs, proximity and search

Three narrower patterns in one folder. Each shows up in a handful of specific
problems, and each has exactly one non-obvious core idea worth carrying out of
here:

- **blobs** — cut on content, not on position
- **proximity** — search nine cells, then filter by exact distance
- **search** — shard by document, and rank in two stages

This is the only step on the [HLD track](../README.md) that is genuinely
optional. Do it after the seven core patterns, not instead of one.

---

# A. Large blobs

**The signal:** files bigger than a request body — images, video, backups,
documents.

**What it fixes:** streaming gigabytes through application servers that should
be handling requests.

## Worked: Dropbox

The core rule, and say it in the first sentence: **bytes never pass through your
application.** The server issues a presigned URL, the client uploads straight to
object storage, and either an S3 event or a client callback updates the metadata.

```
client → POST /files          → { uploadUrl, fileId }   metadata row = PENDING
client → PUT  {uploadUrl}     → object storage          (your servers see none of this)
        S3 event / callback   → metadata row = COMMITTED
```

Everything else in this pattern follows from that split, including the problem
it creates.

## Chunking buys you two things at once

Resumable uploads, and deduplication when you hash each chunk and skip the ones
you already hold. The second is where the interesting decision is.

| | Fixed-size chunks | Content-defined chunks |
|---|---|---|
| Boundary decided by | byte offset | a rolling hash of the surrounding bytes |
| Insert 10 bytes at the front | every subsequent boundary shifts, every hash changes | boundaries re-converge within a chunk or two |
| Bytes re-uploaded after that edit | the whole file | one chunk |
| Complexity | trivial | a rolling hash and a target chunk size |

The demo runs exactly that experiment and prints both numbers. Fixed-size
re-uploads 100% of the file for a ten-byte insertion; content-defined re-uploads
one chunk.

Fixed-size is still the right answer for write-once media — a video that is
uploaded and never edited gains nothing from content-defined boundaries and pays
for the rolling hash. Say which case you are in.

## The consistency gap

The metadata database and the blob store are two systems that can disagree, and
no transaction spans them. Four states you have to handle, and the demo produces
all four:

- **committed** — bytes uploaded, metadata flipped. Fine.
- **crashed mid-upload** — metadata stuck `PENDING`, bytes present or partial.
- **abandoned** — URL issued, user changed their mind, no bytes at all.
- **a lying callback** — a client claims the upload finished when nothing is
  there. A callback is a claim, not a fact, so verify before committing.

And the fourth direction: **orphaned bytes with no metadata row**, which happens
on a reused URL and which nothing in the happy path will ever find.

The answer is a sweeper that runs **in both directions** — expire stale
`PENDING` rows, and delete blobs with no row pointing at them. Candidates
reliably describe the first and forget the second, and the second is the one
that costs money every month.

## Downloads

Through a CDN, with signed URLs when access control matters. The signature is
what lets you cache aggressively at the edge and still revoke access, because
the URL expires rather than the object becoming private.

## Practice

| Problem | What to watch for |
|---|---|
| [Dropbox](https://www.hellointerview.com/learn/system-design/problem-breakdowns/dropbox) **(core)** | The anchor. Presigned uploads, chunking, dedup, sync. |
| [Instagram](https://www.hellointerview.com/learn/system-design/problem-breakdowns/instagram) | Media upload and delivery at consumer scale. |
| [Distributed cloud storage like S3](https://www.youtube.com/watch?v=UmWtcgC96X8) | Building the object store rather than using it. |

## Read

- [Pattern — large blobs](https://www.hellointerview.com/learn/system-design/patterns/large-blobs)
- [Canva scaling media uploads](https://www.canva.dev/blog/engineering/from-zero-to-50-million-uploads-per-day-scaling-media-at-canva/)

---

# B. Proximity and geospatial

**The signal:** "near me", matching riders to drivers, delivery zones,
geofencing.

**What it fixes:** two-dimensional range queries, which ordinary B-tree indexes
handle badly.

## Worked: Yelp nearby search

An index on latitude and one on longitude cannot answer "within 5km of here"
efficiently — you would scan a stripe of the world on one axis and intersect.
The move is to **turn 2D into 1D**.

**Geohash** encodes a box as a string by interleaving latitude and longitude
bits. A shared prefix means nearby, which is what makes it work: it is just a
string, so any database can index it, shard on it, and range-scan it. That is
why it is the default answer.

**Quadtree** subdivides where density is high, so it handles a country with one
enormous city far better than a uniform grid. Harder to distribute.

**S2 and H3** are the production-grade cell systems. H3's hexagons have uniform
neighbour distances, which genuinely matters for delivery zones — with squares,
your diagonal neighbour is 1.41 times further away than your edge neighbour, and
every zone calculation has to account for it.

## The bug that does not throw

A geohash cell has boundaries, and the point closest to your query is very often
on the other side of one. When the high bits flip at a boundary, a nearby place
gets a completely different prefix.

The demo shows a place 556 metres away that a single-cell search cannot see. It
does not throw, does not log, and looks like a slightly thin result set — which
is why it survives to production.

**Search the target cell plus its eight neighbours, then filter by exact
distance.** Nine cells, always. If you say "I'd look up the geohash and return
what's in that cell" and stop, this is the follow-up you have walked into.

## Moving objects are a different problem

Static businesses are a read problem. Drivers pinging their location every four
seconds are a **write** problem with a geospatial index attached.

Keep current positions in memory or Redis with a short TTL, and do not persist
every ping. Say that unprompted and you have skipped ten minutes of being led
there. The history, if you need it, goes to a time-series store on a separate
path — which is [scaling writes](../02-scaling-writes/), not this pattern.

## Practice

| Problem | What to watch for |
|---|---|
| [Yelp](https://www.hellointerview.com/learn/system-design/problem-breakdowns/yelp) **(core)** | The anchor. Static locations, geohash or quadtree, radius queries. |
| [Uber](https://www.hellointerview.com/learn/system-design/problem-breakdowns/uber) | Moving drivers, matching, and the write volume of location updates. |
| [Tinder](https://www.hellointerview.com/learn/system-design/problem-breakdowns/tinder) | Proximity plus a recommendation and swipe-state problem on top. |

## Read

- [Proximity search deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/proximity-search)

---

# C. Search and ranking

**The signal:** full-text search, autocomplete, or a feed ordered by relevance
rather than time.

**What it fixes:** `LIKE '%query%'`, which stops working at about ten thousand
rows.

## Worked: post search

The data structure is an **inverted index**: term to a posting list of document
IDs. Everything else is a consequence of that one choice.

## Shard by document, not by term

This is the decision the question is really about.

| | Shard by document | Shard by term |
|---|---|---|
| Each shard holds | a complete index over its own documents | the full posting list for some terms |
| A query | goes to every shard, each returns its local top-K, a coordinator merges | goes only to the shards owning the query's terms |
| Load with common words | spread almost perfectly | piled onto whichever shard owns "the" |
| Multi-term queries | local intersection, cheap | posting lists must cross the network to intersect |

The demo counts posting entries walked per shard under both. Document sharding
comes out nearly flat; term sharding concentrates on the shard holding the
common words.

State the cost of document sharding yourself, before they raise it: **every
query touches every shard**, so your tail latency is the slowest shard's latency
and adding shards does not reduce per-query fan-out. You accept that because the
alternative has a hot shard you cannot rebalance away, and because top-K merging
is cheap.

## The write path is a pipeline

Ingest, tokenise, normalise, index. Near-real-time indexing means **segment-based
writes with periodic merges** — new documents go into a small new segment that
becomes searchable quickly, and background merges keep the segment count down.

That is how Lucene works, and therefore how Elasticsearch works. Naming the
mechanism rather than the product is the difference between having used it and
having understood it.

## Rank in two stages

Cheap retrieval pulls a few thousand candidates; an expensive scorer ranks only
the top few hundred.

Trying to score everything is the mistake, and it is the one that makes a search
system fall over under load — the expensive scorer's cost scales with corpus
size instead of with result size. Two stages decouple them.

## Autocomplete is not search

Different problem, different structure, and treating it as a search query is the
error.

A **trie held in memory** with the top-K completions precomputed at each node,
rebuilt offline from query logs. The latency budget is single-digit
milliseconds, so nothing touches a database on the request path and nothing is
ranked at query time — the ranking already happened, offline, when the trie was
built.

The demo builds one and walks a prefix down to its precomputed list.

## Practice

| Problem | What to watch for |
|---|---|
| [Facebook Post Search](https://www.hellointerview.com/learn/system-design/problem-breakdowns/fb-post-search) **(core)** | The anchor. Inverted index, sharding strategy, two-stage ranking. |
| [Google Search, focusing on typeahead](https://www.youtube.com/watch?v=CeGtqouT8eA) | Crawl, index, rank — then spend the deep dive on autocomplete: trie, precomputed top-K, offline rebuild from query logs. |
| [News Aggregator](https://www.hellointerview.com/learn/system-design/problem-breakdowns/google-news) | Ranking and personalisation over a fast-changing corpus. |

## Read

- [Elasticsearch deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/elasticsearch)

---

## Run it

```
./run.sh hld/08-blobs-geo-search
```

Seven sections, one per idea above: fixed versus content-defined chunking after
an insertion, the four-way consistency gap and a two-directional sweeper,
geohash encoding and decoding, the boundary miss, document versus term sharding
measured in posting entries walked, two-stage ranking, and a top-K trie.
