# Read-heavy systems

**The signal:** the read-to-write ratio is lopsided — ten to one or worse — or
many users fetch the same object.

**What it fixes:** a primary database melting under traffic that is almost
entirely repeated lookups.

This is the first bottleneck in most consumer systems and the pattern with the
clearest escalation ladder, which makes it the best one to be fluent in. If you
can walk the ladder without being prompted, you have shown the interviewer that
you reach for the cheap fix before the interesting one.

---

## Worked: a URL shortener

One write, millions of reads, and the mapping never changes after creation. That
immutability is the whole reason this problem is easy, and saying so early shows
you have spotted the structure rather than pattern-matched the name. An
immutable value can be cached for a week, replicated anywhere, and served from
the edge, and none of the usual invalidation arguments apply. Most of the
difficulty in caching is invalidation; here there is none.

The arithmetic is in the [estimation cheatsheet](../00-framework/estimation-cheatsheet.md),
and its conclusion is worth repeating because it drives everything below: at 100
million daily users the data fits comfortably on one disk, so this is not a
capacity problem. It is a read-throughput problem, and it has a ladder.

### The ladder, cheapest first

| Rung | What it buys | What it costs |
|---|---|---|
| **Index the lookup** | Turns a scan into a B-tree probe. Often the entire fix. | A little write amplification and some disk. |
| **Read replicas** | Linear read scaling, and you keep one database technology. | Replication lag, and a read-your-writes problem to answer for. |
| **Cache** | Order-of-magnitude latency drop and most traffic never reaching the database. | Staleness, a second system to operate, and the failure modes below. |
| **Edge** | For a pure redirect, a CDN or edge function answers without touching your infrastructure at all. | Invalidation across POPs, and no visibility into the request unless you send it back. |

Skipping straight past the index to Redis is a common mistake and interviewers
notice. State the index, say it is probably enough for the write path, and move
on in one sentence — you get the credit without spending the time.

The cache here wants a long TTL precisely because entries are immutable. Hit
rates are extreme: the top few percent of links are most of the traffic, which
is the same power law that makes the hot key below a problem. Both facts come
from the same distribution, and noticing that out loud is a good moment.

### Which caching strategy, and why

| Strategy | How a write behaves | Pick it when |
|---|---|---|
| **Cache-aside** | Write the database, delete or update the cache key. The application owns both. | The default. Especially when the cache dying should make you slow rather than down. |
| **Read-through** | The cache loads from the database itself on a miss. | You want the loading logic in one place, and your cache library supports it. |
| **Write-through** | Write cache and database together, synchronously. | A stale read straight after a write is unacceptable and you'll pay latency to avoid it. |
| **Write-back** | Write the cache, flush to the database later. | Write throughput is the problem and you can survive losing the last few seconds. Rare outside counters. |

Cache-aside is what I would name first every time, and the argument to give is
about failure rather than speed: with cache-aside the application still knows
how to reach the database, so a dead Redis is a latency incident. With
write-through the cache is on the correctness path, so a dead Redis is an
outage. Say that and the follow-up about Redis failover mostly answers itself.

## The three things they'll push on

### Hot key

One viral link concentrates on a single shard, and no amount of extra Redis
nodes helps because consistent hashing sends every request for that key to the
same node. Two fixes worth naming. A small in-process cache in front of Redis,
with a TTL of a second or two, so a single application server serves thousands
of requests for the hot key from its own heap. And jittered TTLs, so a million
clients don't all miss at the same instant.

If pushed further, the heavier fix is key splitting: store the value under
`key#0` through `key#9` and have each caller pick a suffix at random. Ten copies
on ten shards, ten times the read capacity, at the cost of ten times the writes
on invalidation. Offer it as the escalation rather than the opening move.

### Stampede

When a hot entry expires, every request goes to the database at once. This is
part (b) of the demo in this folder and the numbers are stark: sixteen threads,
sixteen queries, for one key. The standard fixes, in the order I would offer
them:

- **Single flight.** One request refills, the others wait for it and take the
  result. Cheap, exact, and it is the demo's part (c) — sixteen threads, one
  query.
- **Probabilistic early expiry.** Each reader, as the TTL approaches, refreshes
  early with a small and rising probability. The value is refilled before it
  ever expires, so no caller ever waits.
- **Serve stale while revalidating.** Return the expired value immediately and
  refresh in the background. Best latency of the three, and only available when
  a slightly stale answer is acceptable — for an immutable short link, it always
  is.

### Read-your-writes

Someone creates a link and immediately opens it, hits a lagging replica, and
gets a 404. This is the failure that makes users think the product is broken,
and it is invisible in aggregate metrics because it affects one user for a few
hundred milliseconds. Route reads to the primary for a few seconds after a
write, or pin that session to the primary, or carry the write's position in a
cookie and let the router pick a replica that has caught up. The first is what I
would do; the third is what I would mention to show I know the general form.

> **The write problem hiding inside.** Click analytics. Do not increment a
> counter row per click — that's a write-scaling problem in disguise, and it
> belongs on the aggregation path in
> [07-aggregation-and-counting](../07-aggregation-and-counting/).

## The trade-off to name out loud

Every rung on that ladder buys throughput with staleness. A replica is stale by
its lag, a cache is stale by its TTL, and the edge is stale by whatever your
invalidation takes to propagate. The design question is never "is this stale"
but "how stale is acceptable, and for which field". A view count can be a minute
old. A price cannot. Saying which of your fields are which is the part that
sounds like experience.

## The mistake to avoid

Reaching for the cache before knowing the hit rate. A cache in front of a
uniform access pattern with no repeats does nothing except add a network hop and
a new failure mode. The value of a cache is entirely a property of the traffic
distribution, so the sentence to say is "reads are heavily skewed — the top few
percent of keys are most of the traffic — so a cache with a modest memory
footprint gets a very high hit rate here". If you can't say something like that,
you can't justify the box.

The second mistake is quieter: caching an object whose freshness requirement you
never stated. Put the TTL on the whiteboard next to the box. A cache with an
unspecified TTL is an unspecified consistency model.

---

## Run it

```
./run.sh hld/01-scaling-reads
```

Four experiments on the same cache-aside cache, printing the only number that
matters: how many times the database was actually asked.

- **(a)** one key, six reads, two database loads — the ordinary case, and the
  reason to cache at all.
- **(b)** the same naive cache with sixteen threads arriving at the instant the
  key expires. Sixteen misses, sixteen queries.
- **(c)** single flight, same conditions. One query, fifteen threads waiting on
  it, and nobody served stale data.
- **(d)** two hundred keys warmed in the same instant, expiring with a fixed TTL
  and with jitter. The fixed column is a cliff, the jittered column is a ramp.

Time is injected through a `Ticker` so expiry can be forced without sleeping,
which is also the answer to "how would you test this". The only real sleep is
inside `FakeDatabase`, and it is there deliberately: a stampede is a race
between finding nothing and putting something, so an instant query would leave
no window to race in. Part (b) is the one number here that depends on thread
scheduling — sixteen is what you should see, and an occasional fifteen on a busy
machine changes nothing about the point.

The locking in `SingleFlightCache` is the same double-checked pattern that shows
up in [lld/10-concurrency](../../lld/10-concurrency/), and the injected clock is
the same idea as the `Ticker` in [lld/02-strategy](../../lld/02-strategy/).

## Practice

| Problem | What to watch for |
|---|---|
| [Design Bitly / a URL shortener](https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly) **(core)** | The anchor. Do this one first and do it properly; half the sheet builds on it. |
| [Design Instagram](https://www.hellointerview.com/learn/system-design/problem-breakdowns/instagram) **(core)** | Feed read path, media at the edge, and the celebrity problem. |
| [Design a Distributed Cache](https://www.hellointerview.com/learn/system-design/problem-breakdowns/distributed-cache) | Building the cache rather than using it. Consistent hashing, eviction, replication. |

## Read

- [Pattern — scaling reads](https://www.hellointerview.com/learn/system-design/patterns/scaling-reads)
- [Caching strategies](https://algomaster.io/learn/system-design/caching-strategies)
- [Redis deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/redis)
