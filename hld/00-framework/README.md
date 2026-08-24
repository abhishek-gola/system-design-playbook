# The delivery framework

HLD has no equivalent of a two-pointer trick. What it has is a script, and
candidates who don't run one lose the round on pacing rather than on knowledge.
This folder is the script, plus the vocabulary you have to be able to defend
once you start putting boxes on the board.

There is no code here. Nothing about pacing gets better by running a program —
it gets better by doing the thing on a clock, out loud, four or five times. The
[estimation cheatsheet](estimation-cheatsheet.md) next to this file is the one
thing worth memorising rather than deriving.

---

## The forty-five minute script

**The signal:** any prompt that starts with "design" and names a product.

**What it fixes:** the two standard failures. Forty minutes of estimation with
no design, or a box diagram with no reasoning behind any box.

### The clock

| Minutes | What you produce | The failure if you skip it |
|---|---|---|
| 0–5 | Three to five functional requirements in the interviewer's words, written where they can see them. Non-functional ones **as numbers**: p99 under 200ms, 99.9% availability, two years of retention. And what's out of scope. | You design the wrong system, politely, for forty minutes. |
| 5–10 | DAU to QPS to storage — only the numbers that will change a decision. | You pick a database with no evidence, and can't defend it later. |
| 10–15 | Four to six endpoints, then the core entities and their **access patterns**. | You pick the data model from the entity list instead of from the queries, which is backwards. |
| 15–25 | Boxes and arrows, happy path only. Then walk one request end to end out loud. | The interviewer doesn't know what you're building, so every later question lands as an interruption. |
| 25–40 | The deep dive. Either they choose or you offer the two hardest parts. | This is where your level is decided. Losing this time is the single most expensive mistake available to you. |
| 40–45 | Failure and bottlenecks: single points of failure, hot keys, what breaks at ten times the load, what you'd monitor. | You look like someone who has never operated a system. |

Say what's out of scope explicitly. Scoping down is a senior move, not a dodge.
"I'll assume a single region and no GDPR data residency, and I'll come back to
multi-region if we have time" costs you six seconds and buys you the right to
not be ambushed about it at minute thirty.

> **The estimation trap.** Never spend more than eight minutes on
> back-of-envelope maths. Candidates who enjoy it burn fifteen, lose the deep
> dive, and get down-levelled for "lacked depth" — which reads as unfair and
> isn't. If knowing the write rate doesn't change your database choice, don't
> compute the write rate.

### What they're actually scoring

Not whether your architecture matches a reference answer. Whether you noticed
the hard part, named a standard approach for it, and could argue its trade-offs
against the alternative. Fluency over novelty.

Say "this is a contention problem, I'd start with optimistic concurrency" and
you have compressed five minutes into one sentence the interviewer immediately
understands. That sentence also tells them you have seen the shape before,
which is most of what the signal is.

The corollary is uncomfortable but useful: a correct design delivered badly
scores below a conventional design delivered with clear reasoning. The
interviewer is guessing at how you'd behave in a design review with six people
in the room. Behave like that.

### The first sixty seconds

Do not start drawing. Start with two or three questions that change the shape of
the answer, and make them the questions that actually branch:

- Read-heavy or write-heavy, and roughly what ratio? This decides whether you
  are in [01-scaling-reads](../01-scaling-reads/) or
  [02-scaling-writes](../02-scaling-writes/) territory.
- How fresh does the data have to be? Seconds decides whether you need
  [03-realtime-updates](../03-realtime-updates/) or whether a thirty-second
  cache is fine.
- Is anything here money, inventory or seats? If yes, you have a contention
  problem hiding in the prompt and it will be the deep dive.

Three questions, thirty seconds, and you have already narrowed the design space
and shown you know which axes matter. Asking "how many users?" first is the
weakest opening available, because the answer rarely changes anything you do
next.

### The follow-ups you should expect, and how to answer

**"How would you scale this to ten times the traffic?"** Name the component that
breaks first and why, not a generic "add more servers". Usually it is the
primary database or a single hot key. Then give the ladder: index, cache,
replicas, shard — cheapest first.

**"What happens if this component dies?"** For each box, say what the client
sees. Cache dies: latency spikes and the database takes full load, which is
survivable if you sized for it and fatal if you didn't. Queue dies: writes are
rejected at the edge rather than lost. Say which failures are acceptable, not
just which are possible.

**"Why not just use Postgres for everything?"** Take this seriously, because
often the answer is that you should. Argue from access patterns and volume, not
from the reputation of the technology. If you can't produce a number that rules
Postgres out, don't rule it out.

**"How do you know it's working?"** Two or three metrics with thresholds, and
one of them should be user-facing rather than machine-facing. p99 latency on the
read path, consumer lag on the write path, and error rate by endpoint will
cover most designs.

### The trade-off to name out loud

Every design in this repo is a choice about where to pay: at write time or at
read time. Fan-out on write makes reads trivial and writes expensive. Fan-out on
read does the opposite. Caching pays with staleness. Replication pays with lag.
Sharding pays with cross-shard queries. Queues pay with eventual consistency and
the operational cost of a lag alarm.

You are not expected to avoid the cost. You are expected to know which one you
just chose and be able to say why it was the right one for these requirements.

### The mistakes that actually cost rounds

Drawing before scoping is the big one, and it is nearly always fatal. The second
is designing for a scale nobody asked for — a hundred users does not need
Kafka, and reaching for it anyway is marked down as poor judgement rather than
rewarded as ambition. The third is going silent while you think. Ten seconds of
silence is fine, ninety is a data point about how you work.

The last one is subtle: answering the question they asked instead of the
question behind it. When they say "what if this link goes viral" they are not
asking about virality, they are asking whether you know what a hot key is. Name
the concept, then answer.

---

## The vocabulary you must be able to defend

**The signal:** you put a box on the whiteboard and the interviewer says "why?"

**What it fixes:** naming components you can't justify. One unexplained box
undoes three good ones.

For each item below you should be able to explain it in five lines of your own
writing, name one alternative, and say when you'd pick the alternative. That is
the bar. Reading about it doesn't get you there — writing it does.

| Concept | What you must be able to say | The alternative, and when you'd take it |
|---|---|---|
| **Load balancing** | L4 routes packets by connection, L7 reads the request and can route by path or header. What a reverse proxy does that a load balancer doesn't. | L4 when you need raw throughput and protocol independence; L7 when routing depends on the request itself. |
| **API gateway** | Auth, rate limiting, routing, request shaping, all in one place in front of microservices. | Put those concerns in a shared library instead, when you have three services and no platform team to run the gateway. |
| **Caching** | Cache-aside vs write-through vs write-back, eviction policies, TTL, and cache stampede. | Write-through when you can't tolerate a stale read after a write; cache-aside otherwise. See [01-scaling-reads](../01-scaling-reads/). |
| **CDN** | What belongs at the edge: static assets, media, and anything immutable and public. | Origin serving, when content is personalised or access-controlled per user. |
| **Queue vs log** | SQS-style: retries, visibility timeout, DLQ, message disappears when consumed. Kafka-style: ordering per partition, replay, retention. | A queue for work distribution where each message has one owner; a log when several consumers need the same stream, or you need to replay history. |
| **SQL vs NoSQL** | An access-pattern argument, never a slogan. | Relational when queries are varied and joins are real; a wide-column or document store when the access pattern is known, single-key, and enormous. |
| **Indexing** | B+ tree keeps sorted pages and pays on write; LSM buffers in memory and writes sequential sorted files, paying later in compaction. | LSM for write-heavy and append-only workloads; B+ tree for read-heavy with range scans and updates in place. |
| **Sharding** | Key choice, hot partitions, resharding, consistent hashing and what a virtual node is for. | Vertical partitioning or a read replica first, if the pressure is reads rather than volume. See [02-scaling-writes](../02-scaling-writes/). |
| **Replication** | Sync costs latency but loses nothing; async is fast but has a window where a failover loses writes. Replication lag and read-your-writes. | Sync for money; async for almost everything else, with reads pinned to the primary for a few seconds after a write. |
| **Consistency** | Strong vs eventual, and CAP as a statement about behaviour during a partition specifically — not a general licence to pick two. | Strong where a stale read is a correctness bug; eventual where it's a cosmetic one. |
| **ID generation** | Snowflake, ticket server, UUID, and why monotonic IDs matter for index locality. | UUIDv4 when you need offline generation and don't care about index fragmentation; Snowflake when you want roughly time-sortable IDs. |
| **Bloom filters** | Probabilistic set membership: no false negatives, tunable false positives, tiny. The one probabilistic structure that comes up constantly. | An exact index, when the set is small enough to hold and a false positive costs a real lookup you can't afford. |

> **This is your weekly fundamentals slot.** One item, one page, your own words,
> every week. By week ten you'll have a document nobody else has, calibrated to
> how you actually explain things.

### How to test whether you actually know one

Write the page, then close it and answer three questions from memory. What
breaks if I remove this component? What does it cost me that the alternative
doesn't? What would make me change my mind? If any of the three produces a
vague answer, you've read about the concept rather than learned it.

The failure mode is recognisable in interviews: candidates recite the definition
fluently and then stall on "when wouldn't you use it". A definition is the part
you can look up. The boundary is the part they're paying for.

---

## Practice

Both items' problems belong to the same weekly slot: run the script on one, and
write up one vocabulary item in your own words.

| Problem | What to watch for |
|---|---|
| [Run the script on Bitly, timed](https://www.hellointerview.com/learn/system-design/problem-breakdowns/bitly) **(core)** | Then do it again three days later. The second run is where the pacing sticks. |
| [Run the script on a Distributed Cache](https://www.hellointerview.com/learn/system-design/problem-breakdowns/distributed-cache) | Small scope, forces you to fill 45 minutes with depth rather than breadth. |
| [Run the script on LeetCode](https://www.hellointerview.com/learn/system-design/problem-breakdowns/leetcode) | A system small enough that a monolith is defensible. Practise arguing for the simple answer. |
| [Write up caching strategies + stampede](https://algomaster.io/learn/system-design/caching-strategies) **(core)** | Cache-aside, write-through, write-back, and three ways to stop a stampede. |
| [Write up sharding and consistent hashing](https://algomaster.io/learn/system-design/consistent-hashing) **(core)** | Include what happens when you add a node, and what a virtual node is for. |
| [Write up SQL vs NoSQL from access patterns](https://algomaster.io/learn/system-design/sql-vs-nosql) | Pick a real table from a system you have worked on and argue both sides for it. |

## Read

- [Hello Interview — delivery framework](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery)
- [AlgoMaster — answering framework](https://algomaster.io/learn/system-design-interviews/answering-framework)
- [Numbers to know](https://www.hellointerview.com/learn/system-design/core-concepts/numbers-to-know)
- [30 core concepts](https://blog.algomaster.io/p/30-system-design-concepts)
- [Top 15 trade-offs](https://blog.algomaster.io/p/system-design-top-15-trade-offs)
- [Hello Interview — core concepts](https://www.hellointerview.com/learn/system-design/in-a-hurry/core-concepts)
- [Full resource index](https://github.com/ashishps1/awesome-system-design-resources)
