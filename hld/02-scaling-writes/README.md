# Write-heavy systems

**The signal:** ingest rate is the bottleneck, one primary can't absorb it, or
the traffic is bursty and spiky.

**What it fixes:** a single write node, and shard keys chosen by instinct rather
than by access pattern.

Writes are harder than reads, because every write has to land somewhere specific
and coordination gets expensive fast. You cannot put a cache in front of a
write. There is no equivalent of the read ladder in
[01-scaling-reads](../01-scaling-reads/), where each rung buys throughput for a
bit of staleness. Here the decisions are structural, and the first one is the
shard key.

---

## Worked: metrics monitoring

Millions of data points per second, append-only, queried by time range and tag.
A row per point in Postgres is the wrong answer and you should say why in one
line: the index maintenance cost per insert dominates, and you never update a
point once written. Everything a B+ tree is good at — updates in place, exact
lookups, enforcing constraints — is something this workload never asks for, and
everything it is bad at is what this workload does all day.

Say that early. It moves the conversation from "which database" to "what shape
of storage", which is where the interviewer wants you.

## Where the answer actually lives: the shard key

| Key | Spread | Query locality | Verdict |
|---|---|---|---|
| **metric + host** | Thousands of distinct keys, none dominant. | "cpu.user on web-07 for the last hour" is one shard. | The answer. Both goals satisfied. |
| **timestamp** | Every write in the universe goes to the shard holding "now". | Time-range queries are perfectly local, which is why it tempts people. | A hot partition by construction. The mistake interviewers watch for. |
| **tenant** | Natural isolation, easy per-customer operations and deletion. | Everything for one customer is on one node. | Fine until one customer is fifty times the others, and then unfixable by rebalancing. |

The general rule worth stating out loud: **a good key spreads writes evenly and
keeps the rows a single query needs on few nodes.** Those two goals pull against
each other — perfect spread means every query is a scatter-gather, perfect
locality means everything piles onto one node — and naming that tension is the
answer. Candidates who only mention spread have half of it.

The demo in this folder routes the same hundred thousand points through all
three keys across sixteen shards:

| Key | Busiest shard | Idle shards | Keys on the busiest shard |
|---|---|---|---|
| metric + host | 7.7% of writes, 1.2× the average | none | 252 |
| timestamp | 100% of writes, 16× the average | 15 | 1 |
| tenant | 70.3% of writes, 11× the average | 9 | 1 |

The last column is the one to point at. A hot shard holding hundreds of keys is
a capacity problem, and you fix it by rebalancing or adding shards. A hot shard
holding **one** key is a design problem, and no placement scheme rescues it —
consistent hashing, more nodes, better hardware, none of it moves a single key
into two places.

Notice too that the good key is not perfectly flat. It ranges from 5.2% to 7.7%
because hosts produce data at different rates and hashes are not magic. An
imbalance of 1.2× is a system that works. An imbalance of 16× is a system that
falls over while fifteen machines idle.

### Fixing the whale, when tenant is forced on you

Sometimes tenant sharding is not your choice — data residency, per-customer
encryption keys, or the ability to delete one customer cleanly can all force it.
The standard escalations, in the order I would offer them:

- **Composite key.** Shard on `tenant + host` rather than `tenant`. The whale
  spreads across many shards and small tenants still cluster.
- **Split the whale explicitly.** Give the largest tenants a suffix — `acme#0`
  through `acme#15` — chosen per write. This is the same key-splitting trick
  used for hot cache keys in [01-scaling-reads](../01-scaling-reads/), and it
  has the same cost: reads for that tenant now fan out.
- **Dedicated shards.** Big customers get their own cluster. Ugly, operationally
  real, and what most B2B products end up doing.

## The rest of the design

A queue in front, usually Kafka, so a database hiccup queues rather than drops.
This is the single highest-value box on the diagram and it does three jobs at
once: it absorbs bursts, it decouples ingest availability from storage
availability, and it lets consumers batch. Partition it by the same key you
shard by, so ordering per series is preserved and a consumer writes to one
shard. The mechanics of the consumer side belong to
[04-long-running-tasks](../04-long-running-tasks/).

Consumers batch-write, which turns a million small inserts into a few thousand
large ones. The demo prices this out: at a millisecond per round trip, a hundred
thousand single-row inserts take about 101 simulated seconds; at a hundred rows
per batch it is 2 seconds; at a thousand, 1.1. The knee is the point. Batching
attacks the per-round-trip cost only, so once the per-row cost dominates, bigger
batches buy nothing and start costing you memory, lock duration and a coarser
unit of retry. "I'd batch to about a thousand rows, because past that the
returns vanish and the retry granularity gets worse" is a much better answer
than "I'd batch the writes".

An LSM-based or time-series store underneath, because sequential appends are
what it is built for. Writes go to an in-memory table and a write-ahead log,
flush as sorted files, and compact in the background — so the write path is
sequential and the cost is deferred to compaction, which you can schedule. The
trade-off to say out loud is read amplification: a point lookup may touch
several files, which is exactly why LSM stores put a Bloom filter in front of
each one.

Downsampling and retention tiers finish it. Full resolution for a week,
five-minute rollups for a year, hourly beyond that. This is a storage decision
that is really a product decision, so state the retention as a requirement in
minute four rather than discovering it in minute thirty. The rollup machinery
itself is the aggregation path in
[07-aggregation-and-counting](../07-aggregation-and-counting/).

> **The bursts question.** "What happens at ten times the load?" Buffer in the
> queue, let consumer lag grow, alert on it — and be willing to shed load
> explicitly rather than fall over. Saying "I'd drop non-critical metrics before
> I'd drop billing events" is the kind of answer that gets remembered.

## The follow-ups you should expect

**"How do you add shards?"** With modulus, almost every key moves, so use
consistent hashing with virtual nodes and move roughly 1/N of the data. Say what
happens during the move: dual writes to old and new, backfill, then cut reads
over. Resharding a live system is the operational answer, not the algorithmic
one, and interviewers know the difference.

**"What if a write arrives twice?"** Metrics are usually idempotent by key —
same series, same timestamp, same value — so a repeated write is harmless and
you should say so rather than reaching for exactly-once delivery. When the
payload is not idempotent, give the producer a client-generated ID and dedupe on
it. Promising exactly-once end to end is a claim you cannot defend.

**"What about out-of-order points?"** An agent's network drops for two minutes
and then flushes. Your storage must accept a point older than the newest one it
holds, which is fine for LSM and awkward for anything that assumes append-only
in time order. Cap how late you'll accept — an hour, say — and count what you
drop.

**"Why not just add write replicas?"** Because replicas multiply reads, not
writes. Every replica does all the same writes. This confusion is common enough
that having the one-line answer ready is worth real points.

## The trade-off to name out loud

Sharding buys write throughput and sells you cross-shard queries. Every query
that does not include the shard key becomes a scatter-gather across all N nodes,
bounded by the slowest one, and that is where p99 latency goes to die. So the
shard key is chosen by looking at the queries first, not the writes — and if two
query patterns want different keys, you either denormalise into two stores or
you accept the fan-out for the rarer one. Say which, and why.

## The mistake to avoid

Choosing the shard key from the entity and not from the access pattern. "It's
time-series data, so I'll shard by time" sounds like domain knowledge and is the
exact failure this demo prints in a histogram. The corrective habit: before you
name a key, say the two most common queries out loud, then check that each one
either includes the key or is rare enough to fan out.

The second mistake is skipping the queue because "the database can handle it".
Perhaps it can, at the average rate. The queue is not there for the average, it
is there for the Monday morning spike and the twenty minutes your storage is
being failed over.

---

## Run it

```
./run.sh hld/02-scaling-writes
```

A hundred thousand synthetic metric points routed across sixteen shards under
each of the three keys, printed as a histogram with the distinct-key count per
shard, then the batching cost model. The workload has one whale tenant at about
seventy per cent of traffic, because every real multi-tenant system has one and
a shard-key argument that assumes uniform tenants is an argument about a system
nobody runs.

Everything is seeded with `new Random(42)` and the synthetic clock is a counter
rather than the wall clock, so the histograms are identical on every machine and
the numbers quoted above stay true. The batching section is a cost model, not a
benchmark — a fixed price per round trip plus a variable price per row — and you
should label it that way if you use the shape of it in an interview.

## Practice

| Problem | What to watch for |
|---|---|
| [Design Metrics Monitoring (Datadog)](https://www.hellointerview.com/learn/system-design/problem-breakdowns/metrics-monitoring) **(core)** | The anchor. Shard key choice, time-series storage, downsampling. |
| [Design Strava](https://www.hellointerview.com/learn/system-design/problem-breakdowns/strava) | High-volume activity ingest plus geospatial queries on top. |
| [Read: how Discord stores trillions of messages](https://discord.com/blog/how-discord-stores-trillions-of-messages) | A real write-scaling migration, with the reasoning intact. Worth more than three tutorials. |

## Read

- [Pattern — scaling writes](https://www.hellointerview.com/learn/system-design/patterns/scaling-writes)
- [Database sharding](https://algomaster.io/learn/system-design/sharding)
- [Cassandra deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/cassandra)
