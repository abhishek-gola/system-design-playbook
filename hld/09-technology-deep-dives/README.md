# Defending the technologies you claim

**The signal:** it's written on your CV.

**What it fixes:** the worst outcome available to you in an interview — being
caught shallow on the thing you listed as your specialism.

Everything on that page is fair game for a forty-minute interrogation. Kafka and
Flink are the two they will go hardest at, because almost nobody at your level
has actually run them in production and an interviewer who has will want to find
out within about four questions whether you did too. That cuts both ways. If you
hold up, the round is close to won before you draw a box; if you fold on
watermarks or on what a rebalance costs, nothing you say afterwards recovers it,
because the doubt is now about honesty rather than knowledge.

## The bar

For each technology on your CV you should be able to explain the mechanism in
**five lines of your own writing, name one alternative, and say when you would
pick the alternative**. That is the whole bar and it is higher than it sounds.
Reading a deep-dive page does not get you there. Writing the five lines does,
because writing is where you find out which parts you were quietly skipping.

Two failure modes to recognise in yourself while you draft:

- **Recipe knowledge.** You know the config that fixed it — `acks=all`,
  `min.insync.replicas=2` — but not what it is protecting you from, so the first
  "why does that help?" leaves you repeating the setting louder.
- **Documentation recall.** You can define a watermark. You cannot say what
  went wrong on your own pipeline that made you care about one.

The cure for both is the same and it is in the templates next to this file.

## The move that wins the round

For each technology, prepare one story: something that broke in production, why,
and what you changed. "We had consumer lag spike during a promo because one
partition was hot on a popular restaurant ID" beats any amount of documentation
recall, and nobody who has not run these systems can produce it. It also changes
the shape of the conversation — you stop being examined and start being
consulted, which is exactly the register a senior candidate wants.

Keep the story to sixty seconds and make sure it has a diagnosis in the middle,
not just a symptom and a fix. The interesting part is never "we scaled the
consumers"; it is how you worked out which of the four plausible causes it was.

---

# The question bank

Four sections, one per technology, matching the four template files. Under each
question is a short note on the ground a good answer has to cover. The notes
deliberately stop short of answering — you writing the answer is the entire
exercise, and an answer you read here would be in someone else's words when you
need it in yours.

## Kafka

**"If I publish two events for the same order ID, am I guaranteed to read them
in that order?"**

The answer has to get to per-partition ordering rather than global ordering, and
then to key choice as the thing that actually decides it. Cover what a null key
does, and what you give up if you try to buy global ordering. The follow-up you
should walk into deliberately is the cost of keying by a real entity, because it
sets up your hot-partition story.

**"One of your consumers dies mid-batch. Walk me through what happens."**

Cover the group coordinator noticing, the reassignment, and what happens to
records that were processed but whose offsets were never committed. Then the
part most candidates miss: what a rebalance costs you in practice — how long
consumption stops, and what a slow processing loop does to
`max.poll.interval.ms`. Naming cooperative rebalancing as the improvement is a
good place to land.

**"You've said the pipeline is exactly-once. Exactly-once between which two
points?"**

This question is a trap for people who read the marketing. Separate the
idempotent producer from transactions, say what each one actually removes, and
be honest that the guarantee ends where the sink stops participating. Have a
position on when you would not bother, and reach for idempotent writes
downstream instead.

**"When would you use a compacted topic rather than just setting a long
retention?"**

Cover the difference between a replay window and a snapshot of current state per
key, tombstones and what deletion really means, and the fact that compaction is
a background process with lag rather than a guarantee about what is on disk
right now. Name one thing in your own system that should have been compacted, or
one that is compacted and should not be.

**"What exact configuration stops you losing an acknowledged write, and what
does it cost you?"**

The trap is answering `acks=all` and stopping. Cover the ISR, what happens when
it shrinks, why the replication factor and `min.insync.replicas` have to be
discussed as a pair, and unclean leader election. The second half of the
question matters as much as the first: say plainly what availability and latency
you paid for that durability, because a candidate who only lists safety settings
has never had to keep a producer up during a broker restart.

**"Your lag alarm is firing. Take me through the first ten minutes."**

This is the one where production experience shows, so answer it as a diagnosis,
not a list. Cover how you tell whole-topic lag from single-partition lag, how
you separate a slow consumer from a slow downstream dependency from a poison
record, and why adding consumers stops helping at a particular number. Finish
with the decision you actually took.

## Flink

**"Why does Flink make you choose a time semantic at all? Which one do you use,
and why?"**

Cover determinism — whether re-running the job over the same data has to give
the same answer — and what that means for backfills. Then be honest about the
price of event time, which is waiting, and name the case in your own system
where processing time was the right call because the result was an alert rather
than a number anyone reconciles.

**"An event turns up an hour late. What happens to it?"**

Cover what a watermark is claiming, where it is generated and how the minimum
across parallel sources controls the job, and what an idle partition does to it.
Then allowed lateness and side outputs. The mechanics are the easy half; the
half that gets scored is what you do with the side output, because "we route it
somewhere" and "we reconcile it into the aggregate within X" are very different
answers.

**"What changes when your keyed state stops fitting in memory?"**

Cover the state backend switch, what serialisation on every access does to your
throughput, and incremental checkpoints. The senior version of this answer goes
after the cause rather than the symptom: key cardinality and state TTL are what
actually bound the problem, and a design that lets state grow without a bound is
the real bug.

**"How does a checkpoint work, and how does that become end-to-end
exactly-once?"**

Cover barriers, alignment and what an unaligned checkpoint is for, and the fact
that source offsets are part of the snapshot. Then two-phase commit on the sink
side, and the precise window where a failure produces duplicates anyway. Say
what your sink actually is and whether it can participate.

**"The job is slow. How do you tell a slow sink from a hot key?"**

Cover how backpressure propagates upstream and why the first operator that is
busy but not backpressured is the one you want. Skew and slowness look identical
on a throughput graph and completely different on per-subtask metrics, so say
which metric separates them. Then the remedies, and which of them you have
actually applied.

## Redis

**"Redis is single-threaded. Why is it still fast, and when does that hurt
you?"**

"It's in memory" is half an answer and interviewers know it, so cover the rest:
no lock contention, no context switching, and commands that are individually
tiny. The second half is where the marks are — one slow command blocks every
other client, so name the commands that can do that and what you do instead.
Threaded I/O in recent versions is worth mentioning only if you are precise
about what it does and does not parallelise.

**"Which structure would you use for this, and why not a sorted set?"**

Have three or four concrete pairings ready from your own work rather than a tour
of the docs — the sliding-window counter, the compact object with field-level
updates, the stream with consumer groups, the approximate unique count. For the
probabilistic one, state the error and the memory in the same breath, and say
what you permanently give up by using it.

**"The Redis box hard-reboots. How much do you lose?"**

Cover RDB and AOF as different bets, the fsync policy and what "everysec"
concretely costs you, and rewrites. Then the point that matters more than either
in a cluster: replication is asynchronous, so a failover can lose writes no
matter which persistence mode you chose. Close by saying whether that is
acceptable for what you keep in there, because for a cache it plainly is and for
a counter someone bills against it plainly is not.

**"Why can't I run `MULTI` across these two keys?"**

Cover hash slots and how a key maps to one, why multi-key operations need every
key in the same slot, and hash tags as the deliberate fix. Then resharding: what
moves, what a client sees while it moves, and what that does to a live
workload — that last part is the bit that sounds like experience.

**"How do you make a read-modify-write atomic? And would you use Redlock for a
distributed lock?"**

For the first half, cover Lua running to completion on the event loop, and
therefore the obligation to keep the script short. Mention the optimistic
alternative. For Redlock, take an actual position rather than reciting the
argument on both sides: name the assumptions it needs about clocks and pauses,
and separate locks that are an optimisation from locks that correctness depends
on. Fencing tokens are the phrase to know.

## DynamoDB

**"Model this access pattern in a single table."**

Cover the fact that you enumerate the queries before you design the keys, what
the partition key and sort key each buy you, and key overloading. Then the
honest cost: an access pattern nobody anticipated is a new index or a migration,
not a new `WHERE` clause. Interviewers are checking whether you understand that
this is a trade you made, not a free win.

**"GSI or LSI here, and what does each cost you in consistency?"**

Cover the different key freedom, when each can be created, the consistency each
offers, and the item collection limit that comes with the local one. The
sentence that separates a real user from a reader is what a throttled global
index does back to writes on the base table. Projections and read amplification
are worth a line.

**"One customer is a large share of your traffic. What happens?"**

Cover the per-partition ceiling, and treat adaptive capacity and burst as
mitigations rather than a fix. Then write sharding, and a cache on the read
side. Finish on on-demand versus provisioned as a cost and predictability
decision, including how autoscaling behaves on a spike that arrives faster than
it reacts — which, on a food delivery platform, is every promotion.

---

## How to work this folder

Four templates sit next to this file, one per technology. Fill each one in your
own words, five lines maximum per section — the limit is the point, because
anything you cannot compress to five lines you do not yet understand well
enough. Then finish every file with one production story.

- [kafka.md](kafka.md) — includes a worked example story, clearly marked, so you
  can see the shape expected before you write your own.
- [flink.md](flink.md)
- [redis.md](redis.md)
- [dynamodb.md](dynamodb.md)
- [question-bank.md](question-bank.md) — the flat list of every question above,
  with nothing else on the page. Read one, answer it out loud, tick it off. Any
  question where you hear yourself hedging goes back into the template.

Do Kafka first, then Redis, then Flink. Kafka because it is what they will open
with, Redis because it is the cheapest conversion of recipe knowledge into model
knowledge you will get this quarter, and Flink last because it takes the longest
and by then you will have the writing habit.

Your fraud work is where most of these stories live, so expect this folder and
[hld/10-signature-design](../10-signature-design/) to feed each other. A story
that is too long for a deep-dive answer is often exactly the right size for a
follow-up in the signature design round.

## Read

- [Kafka deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/kafka)
- [Flink deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/flink)
- [Redis deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/redis)
- [DynamoDB deep dive](https://www.hellointerview.com/learn/system-design/deep-dives/dynamodb)

## Practice

| Task | What to watch for |
|---|---|
| [Write up Kafka in your own words](https://www.hellointerview.com/learn/system-design/deep-dives/kafka) **(core)** | One page. Then find the two things you couldn't explain and fix those. |
| [Write up Redis internals in your own words](https://www.hellointerview.com/learn/system-design/deep-dives/redis) **(core)** | The cheapest conversion of recipe knowledge into model knowledge you'll get this quarter. |
| [Write up Flink and one production story](https://www.hellointerview.com/learn/system-design/deep-dives/flink) **(core)** | Event time, watermarks, checkpointing — plus the incident that taught you each. |
