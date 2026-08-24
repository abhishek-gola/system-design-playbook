# High-level design

Eleven folders, mirroring the sheet's steps. HLD has no equivalent of a
two-pointer trick. What it has is a script, and candidates who don't run one
lose the round on pacing rather than knowledge — so
[00-framework](00-framework/) is not optional and not a warm-up.

| Step | Week | Folder | The pattern |
|---|---|---|---|
| 00 | 4 | [00-framework](00-framework/) | The forty-five minute script, and the vocabulary you must be able to defend |
| 01 | 5–6 | [01-scaling-reads](01-scaling-reads/) | Read-heavy systems — caches, replicas, the edge |
| 02 | 5–6 | [02-scaling-writes](02-scaling-writes/) | Write-heavy systems — shard keys, queues, LSM stores |
| 03 | 5–6 | [03-realtime-updates](03-realtime-updates/) | Pushing updates to clients — the transport ladder, and routing |
| 04 | 5–6 | [04-long-running-tasks](04-long-running-tasks/) | Work that outlives the request — queues, idempotency, DLQs |
| 05 | 8–10 | [05-contention](05-contention/) | Scarce resources and many claimants — holds, locks, fencing |
| 06 | 8–10 | [06-multi-step-processes](06-multi-step-processes/) | Sagas, compensations, the outbox |
| 07 | 8–10 | [07-aggregation-and-counting](07-aggregation-and-counting/) | Streaming aggregation, windowing, sketches, rate limiting |
| 08 | 8–10 | [08-blobs-geo-search](08-blobs-geo-search/) | Three narrower patterns: large blobs, proximity, search |
| 09 | 4 and 8 | [09-technology-deep-dives](09-technology-deep-dives/) | Kafka, Flink, Redis, DynamoDB — defending the tools you claim |
| 10 | 8–10 | [10-signature-design](10-signature-design/) | One design nobody else can give as well as you |

## Core path

`00-framework` · `01-scaling-reads` · `02-scaling-writes` · `03-realtime-updates` ·
`04-long-running-tasks` · `05-contention` · `06-multi-step-processes` ·
`07-aggregation-and-counting` · `09-technology-deep-dives` · `10-signature-design`

Step 08 is the only one that's genuinely optional, and only because its three
topics each show up in a smaller set of problems.

## Picking the pattern from the prompt

The same trick as the LLD table: you get a product name, and the job is to hear
which pattern it's really asking for. Most real prompts are two of these
stacked, so name both.

| What they ask for | The pattern underneath |
|---|---|
| Bitly, Instagram feed, a distributed cache | scaling reads |
| Datadog, Strava ingest, anything append-only at volume | scaling writes |
| WhatsApp, live comments, presence, Google Docs | real-time updates |
| YouTube upload, a job scheduler, a web crawler | long-running tasks |
| Ticketmaster, an auction, Robinhood order matching | contention |
| A payment system, Uber's ride lifecycle, order fulfilment | multi-step processes |
| Ad click aggregation, top-K, a distributed rate limiter | aggregation and counting |
| Dropbox, Yelp, post search, typeahead | blobs, geo, search |

## The two things being scored

Not whether your architecture matches a reference answer. Whether you **noticed
the hard part**, named a standard approach for it, and could argue its
trade-offs against the alternative.

Fluency over novelty. Saying "this is a contention problem, I'd start with
optimistic concurrency and move to a hold with a TTL when the payment call goes
in the middle" compresses five minutes into one sentence the interviewer
immediately understands.

## Where the code is

Most folders have runnable Java, because a lot of HLD reasoning turns out to be
testable at small scale — a shard key that creates a hot partition does so just
as visibly across eight in-memory maps as across eight database nodes, and a
watermark either drops a late event or routes it to a side output whether the
stream is Kafka or an ArrayList.

```
./run.sh hld/01-scaling-reads
./run.sh                       # lists every folder with a Demo.java
```

Three folders are prose only, and deliberately so:

- [00-framework](00-framework/) — a script and a vocabulary list, plus an
  estimation cheat sheet
- [09-technology-deep-dives](09-technology-deep-dives/) — question banks and blank
  templates you fill in yourself, because writing it in your own words is the
  entire exercise
- [10-signature-design](10-signature-design/) — a design document to write, not
  a program to run

## The related LLD folders

Several patterns here are the same idea one zoom level down, and knowing both
makes each answer shorter:

- [05-contention](05-contention/) ⟷ [lld/10-concurrency](../lld/10-concurrency/) —
  the same seat-booking problem, one process versus many
- [03-realtime-updates](03-realtime-updates/) and
  [04-long-running-tasks](04-long-running-tasks/) ⟷
  [lld/05-observer](../lld/05-observer/) — Observer is a message queue in one JVM,
  bounded queues and overflow policy included
- [07-aggregation-and-counting](07-aggregation-and-counting/) ⟷
  [lld/02-strategy](../lld/02-strategy/) — the rate limiter, at two scales
- [10-signature-design](10-signature-design/) ⟷
  [lld/07-chain-of-responsibility](../lld/07-chain-of-responsibility/) — the risk
  rule engine is literally the same design
