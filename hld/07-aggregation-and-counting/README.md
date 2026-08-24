# Streaming aggregation

**The signal:** "how many", "top K", "per second", "in the last five minutes",
"block after N attempts".

**What it fixes:** counting on the write path, which turns every event into a
database contention problem.

This is your home turf, and the step where two years of Kafka and Flink in
production stops being a line on a resume and starts being an advantage.
Prepare it until you can drive it cold.

The reason it is worth more effort than the other patterns is that the ceiling
is higher. Most candidates can describe the boxes — Kafka, a stream processor, a
read store — and most of them stall the moment somebody asks how exactly-once
actually works, or what a watermark is a statement about. You can go past that
point, and the difference is very visible from the other side of the table.

---

## Worked: ad click aggregation

Never increment a row per click. Events go to Kafka, a stream processor
aggregates, results land in a store built for the query. The whole design is
about moving the counting off the critical path.

```
click → Kafka (partitioned by adId) → Flink → aggregated store → query API
                                        ↓
                                   raw events → S3 (for reprocessing)
```

Three things are worth pointing at explicitly, because each of them is a
follow-up waiting to happen.

**The partition key is adId, not userId.** Every click for one ad lands on one
partition, so one operator instance owns that ad's running count and no shuffle
is needed to aggregate it. Key by userId instead and each ad's clicks are spread
across every partition, which means a network shuffle before you can count
anything. The cost of keying by adId is that a viral ad becomes a hot partition,
and the fix for that is a composite key — `adId:bucket` with a small random
bucket suffix — plus a second aggregation step that sums the buckets. Say that
before they ask.

**The raw events go to object storage as well.** Not as a backup, as a
reprocessing path. Every streaming aggregation is a guess that becomes stale the
moment you find a bug in the job or an event that arrived too late to count. The
S3 copy is what lets a batch job recompute a day from scratch and overwrite the
streamed numbers. That is what people mean by lambda architecture, and naming it
is faster than describing it.

**The read store is chosen for the query, not for the write.** Aggregated
counts by ad and minute is a small, wide, append-mostly dataset with a
predictable access pattern, so almost anything works — Cassandra keyed by
`(adId, minute)`, or DynamoDB with the same key, or Redis if the retention is
short. Note out loud that this store handles a tiny fraction of the write volume
the raw topic does, because the aggregation already collapsed a million clicks
into sixty rows. That collapse is the entire value of the pattern.

## Windowing, said precisely

| | Boundaries | Each event lands in | Watch for |
|---|---|---|---|
| **Tumbling** | fixed, non-overlapping | exactly one window | nothing much — this is the cheap one, and counts across windows sum to the event total |
| **Sliding** | fixed, overlapping | `size / slide` windows | state and write amplification multiply by that ratio. Five minutes sliding every thirty seconds is 10x |
| **Session** | inactivity gaps | one session, which may merge two existing ones | the assigner is not a function of the timestamp alone, so state changes shape rather than only growing |

Tumbling is the default and you should say so. Sliding is what you reach for
when the question is "in the last five minutes" rather than "in the minute of
14:03", and the honest engineering answer when the ratio gets expensive is to
keep per-slide buckets and sum the last N on read — which works because counts
and sums are invertible, and does not work for maxima or distinct counts.

Session windows are the one that behaves differently. An event does not simply
join a window; it opens one and then absorbs any existing session it now
touches, which means an event landing in the gap between two sessions merges
them into one. `SessionWindowAggregator` implements that merge properly rather
than assuming events arrive in order, because assuming they arrive in order is
the bug.

## Event time versus processing time

Processing time asks "what time is it here, now". Event time asks "when did this
happen". The distinction exists because those two answers diverge, and every
interesting property of a streaming system comes from how you handle the gap.

The demo runs both over identical events and the shapes are completely
different. Event time shows three bursts of clicks with quiet minutes between
them, which is what the users actually did. Processing time shows a flat smear,
because the consumer was reading at a steady rate — a fact about your
infrastructure, not about your advertisers. Replay the same topic tomorrow after
a backlog and the processing-time numbers change while the event-time numbers do
not.

For anything you bill on, that settles it. Processing time is still correct for
some questions — a consumer's own throughput, or a window whose meaning
genuinely is "in the last minute of wall-clock time" — and knowing when it is
acceptable is usually the follow-up.

## Watermarks, allowed lateness and side outputs

A watermark is not a clock and not a filter. It is the operator's assertion:
*I do not expect to see any more events with a timestamp below this.* Windows
fire when the watermark passes their end, because that is the moment the
operator believes the window is complete.

The usual generator is the obvious one: the highest event time seen so far,
minus a fixed tolerance for out-of-orderness. That tolerance is a straight
latency-for-completeness trade and it is worth stating as one. Thirty seconds
means every result is thirty seconds stale and you catch nearly every straggler.
One second means fresh results and a lot more corrections.

Three things can happen to an event, and being able to list all three is most of
the answer:

| Where it lands | What happens |
|---|---|
| before the watermark, window still open | counted normally |
| after the window fired, but inside the allowed lateness | counted, and the window re-fires with a corrected total |
| after window end plus allowed lateness | the state has been dropped, so there is nothing left to correct |

That third row is where the interesting decision lives. Flink's default is to
drop those events silently, which is a reasonable default and a terrible thing
to discover in production. Route them to a side output instead and you can count
them, alert when the rate moves, and feed them to the batch path. The demo
deliberately injects one event that lands in the second row and one that lands
in the third, then compares the streamed result against the same aggregation
over perfectly ordered events, so you can see exactly one click go missing and
know precisely which one.

The other reason allowed lateness matters is memory. A window's state can only
be released once the watermark has passed its end plus the lateness, so those
two settings are what bound your state size. "Why is this job's state
unbounded" is almost always a window that never gets a watermark past its end —
usually an idle partition, since the watermark of an operator with several
inputs is the *minimum* across them, and one silent partition holds the whole
job back. The fix is an idleness timeout, and it is a good thing to have opinions
about.

## Exactly-once, mechanically

Most candidates can say the phrase. Very few can say what it is made of, and the
difference is the single most visible thing in this whole step.

The mechanism, in order:

1. The checkpoint coordinator injects a **barrier** at the source.
2. The barrier flows through the graph in line with the records. Each operator
   **snapshots its state** the moment the barrier reaches it, then forwards the
   barrier on. An operator with several inputs **aligns** the barriers: it holds
   back the input that got there first until the barrier arrives on the others,
   so the snapshot is consistent. That alignment is where checkpointing shows up
   in your p99, and unaligned checkpoints are the trade you make when it hurts.
3. The sink **pre-commits**: what it has buffered becomes durable but stays
   invisible to anyone reading it.
4. Every operator acknowledges, the coordinator declares the checkpoint
   **complete**, and only then does the sink **commit**.
5. On restart, source offsets and operator state come back from the same
   checkpoint, and any transaction not covered by it is aborted.

Step 4 is why this is an end-to-end guarantee rather than a job-local one. The
sink commits transaction N only when the offsets and the operator state for the
same N are already durable, so replay can never publish anything twice.

`Demo` runs an identical crash through two sinks so the difference is not a
matter of opinion. The naive sink writes on every record and finishes over by
exactly the number of replayed records. The two-phase-commit sink finishes
exactly right, having aborted one transaction on restore. Both jobs recovered
their own state correctly — the difference is entirely about what the sink had
already made visible.

| Guarantee | How you get it | What it costs |
|---|---|---|
| At most once | fire and forget, no replay | you lose data on any failure |
| At least once | checkpointed state, replay on restart, sink writes immediately | duplicates after every failure |
| Effectively once, via idempotent sink | sink does an upsert of a computed value keyed by window, not an increment | needs the operation to be naturally idempotent |
| Effectively once, via two-phase commit | the five steps above | results only appear at checkpoint boundaries, and the destination must support transactions |

Call it **effectively once**, not exactly once. Records genuinely are processed
more than once; what happens once is the effect on the outside world. Making
that distinction unprompted is worth more than any amount of terminology recall.

And the idempotent-sink row deserves more respect than it usually gets. If the
sink can do `SET count[ad][minute] = 4217` rather than `INCR count[ad][minute]`,
replay is harmless and you need none of the machinery. "I made the sink
idempotent instead" is a strong answer as long as you can explain why an
increment is not idempotent and an upsert is.

## When exact is too expensive

| Structure | Answers | Memory | Error |
|---|---|---|---|
| **HyperLogLog** | how many distinct | fixed, `2^p` bytes | `1.04/sqrt(m)`, and it does not grow with cardinality |
| **Count-Min Sketch** | how often have I seen this key | fixed, `depth × width` counters | one-sided: never undercounts |
| **Top K** | which keys are hottest | sketch plus K candidates | approximate twice over |

HyperLogLog's intuition, which is what you should offer rather than the algebra:
hash every value and look at the leading zeros. One in `2^k` hashes starts with
k zeros, so the longest run you have ever seen is evidence about how many
distinct values you have seen. Split the hash so the top bits pick one of m
registers, keep the longest run per register, and take a harmonic mean across
them to damp the outliers. The two properties worth naming are that the error
depends only on the register count and not on the cardinality, and that two
sketches merge by taking the per-register maximum — which is why every large
distinct-count system shards on sketches rather than on exact sets. The demo
merges two shards with overlapping audiences and shows the naive sum roughly
doubling the true answer while the merge gets it right.

Count-Min Sketch's one sentence is that **the error is one-sided**. Every cell a
key touches holds that key's count plus whatever collided into it, so taking the
minimum across rows gives you the reading least polluted by collisions, and it
is always at or above the truth. It will tell you a rare key is more common than
it is; it will never tell you a heavy hitter is rare. For "find the abusive IPs"
or "find the trending videos" that is exactly the direction you want to be wrong
in. The demo prints head and tail estimates side by side so you can see the same
absolute overcount being noise on one and a large relative error on the other.

Top K is the pairing, and the gap it fills is worth being precise about: a
Count-Min Sketch cannot list anything, because there are no keys inside it. So
you carry a bounded candidate set alongside — in production a Redis sorted set,
`ZADD` the estimate then `ZREMRANGEBYRANK` to trim back to K. If the ordering
has to be right, use the sketch to shortlist a few hundred candidates and count
that shortlist exactly.

## Rate limiting is this pattern, small

Token bucket in Redis with a Lua script for atomicity; sliding window log when
you need precision and can afford a timestamp per request; fixed window counter
when you cannot. The algorithms themselves are implemented properly one level
down, in [lld/02-strategy](../../lld/02-strategy/), all three behind one
interface with an injected clock.

The real question at this level is always the same: where does the counter live
when fifty API servers are enforcing one limit? The answer is a shared store
with a local fast path that accepts some overcounting at the edges, and the
demo puts numbers on it — how many requests get through above the limit, and how
many calls to the shared store each approach costs. Turning the sync interval
down shrinks the overshoot and raises the call volume. There is no setting that
gives you both.

Whether the overshoot is acceptable is a product question rather than an
engineering one. A public API's thousand-per-minute is a fairness measure and
nobody is harmed by 1040. A payment provider's contractual limit is a promise,
and you pay for the round trip on every request.

One more thing to have an answer ready for: what the gateway does when Redis is
unreachable. Fail open and an outage becomes a free-for-all; fail closed and a
Redis blip takes the product down. For rate limiting the usual answer is fail
open, because the limiter protects you from load rather than from fraud — but
have decided it in advance, and say which and why.

## The follow-ups they actually ask

**"What happens when one ad goes viral and its partition can't keep up?"**
Composite key. Write to `adId:0` through `adId:15` with a random or hashed
suffix, aggregate each independently, then sum the sixteen sub-counts at read
time or in a second aggregation stage. You have traded a little read complexity
for sixteen times the write parallelism. The other half of the answer is that you
should detect it rather than guess: consumer lag per partition is the metric, and
a single partition's lag climbing while the others are flat is the signature.

**"Your job is falling behind. What do you look at?"** Consumer lag first, per
partition, because it tells you whether the problem is skew or the whole job.
Then backpressure, which in Flink propagates upstream from whichever operator is
the bottleneck — the last operator that is *not* backpressured is the culprit,
and the ones behind it are just victims. Then checkpoint duration, since a
checkpoint that has started taking minutes usually means state has outgrown
memory or alignment is stalling on a slow input.

**"State stopped fitting in memory. Now what?"** Switch the state backend to
RocksDB, which spills to local disk and keeps only a working set in memory. What
changes is that every state access becomes a serialise-and-deserialise, so
throughput drops noticeably, and checkpoints become incremental — you ship
changed SST files rather than the whole state. Before doing that, check whether
the state is large because it needs to be or because a window never closes.

**"How do you fix a day of numbers after finding a bug in the job?"** Reprocess
from object storage into a separate output table, verify, then swap. Do not
mutate the live table in place, and do not try to make the streaming job
backfill — it is optimised for a completely different access pattern. This is
the one place where having the raw events in S3 stops being theoretical.

**"How fresh are these counts?"** Checkpoint interval plus watermark tolerance,
roughly, because two-phase commit means nothing is visible until a checkpoint
completes. That is a real number you can quote, and quoting it demonstrates you
understand that the exactly-once machinery is not free.

## The trade-off to name out loud

Completeness against latency, and it is one dial with your hand on it. Every
mechanism in this folder is a position on that dial: the watermark tolerance,
the allowed lateness, the checkpoint interval, the choice between exact counts
and sketches, the sync interval on the rate limiter.

The strong version of the answer is not picking a setting. It is saying what the
number means to the business. Advertiser-facing billing numbers can be a minute
stale and must be right, so long tolerance, exact counts, two-phase commit. A
real-time bidding dashboard needs to be a second fresh and can be a percent
wrong, so short tolerance, sketches, idempotent upserts. Same architecture, two
sets of constants, and the constants come from the requirement rather than from
a default.

## The common mistakes

Saying "exactly once" and stopping. If you cannot say barrier, snapshot,
pre-commit, commit, you have not answered the question, and the interviewer knows
it immediately.

Counting on the write path because the volume sounds manageable. It is
manageable right up until one ad is popular, at which point every writer is
contending on one row.

Treating late events as an edge case worth a sentence. It is the core of the
design. Where they go, who notices, and what repairs the number is most of what
separates a real answer from a diagram.

Reaching for HyperLogLog when the cardinality is small. If you can hold the set,
hold the set — the sketch is for when you cannot, and using it to count a
thousand things is a worse answer, not a cleverer one.

Sliding windows without mentioning the multiplier. Anyone who has operated one
knows the state cost; not mentioning it is a tell.

---

## Run it

```
./run.sh hld/07-aggregation-and-counting
```

Eight sections, all deterministic, no clock and no threads. The synthetic stream
is sixty ad clicks in three bursts, deliberately delivered out of order, with one
straggler placed inside the allowed lateness and one placed far outside it.

The parts worth reading the code for are `EventTimeWindower`, which implements
the watermark, the allowed lateness and the side output in about forty lines, and
`TwoPhaseCommitSink` next to `NaiveSink`, which are put through an identical
crash so the double count is a printed number rather than an assertion.

## Practice

| Problem | What to watch for |
|---|---|
| [Design an Ad Click Aggregator](https://www.hellointerview.com/learn/system-design/problem-breakdowns/ad-click-aggregator) **(core)** | The anchor. Windowing, watermarks, exactly-once, and the reprocessing path. |
| [Design YouTube Top K](https://www.hellointerview.com/learn/system-design/problem-breakdowns/top-k) **(core)** | Sketches plus sorted sets. The one where approximate structures earn their keep. |
| [Design a Distributed Rate Limiter](https://www.hellointerview.com/learn/system-design/problem-breakdowns/distributed-rate-limiter) **(core)** | Your specialism, at system scale. Make this an answer you can give without thinking. |

The rate limiter is the one to over-prepare, because it is the cheapest bridge
between this folder and both [lld/02-strategy](../../lld/02-strategy/) and your
own production experience. Being able to switch between the algorithm, the
distributed counter and the incident you remember, in one answer, is unusual.

## Read

- [Flink deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/flink)
- [Kafka deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/kafka)
- [Rate limiting algorithms with code](https://blog.algomaster.io/p/rate-limiting-algorithms-explained-with-code)
- [Data structures for big data](https://www.hellointerview.com/learn/system-design/deep-dives/data-structures-for-big-data)
