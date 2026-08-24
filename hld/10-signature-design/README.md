# Real-time fraud detection at scale

**The signal:** "tell me about something you've built", or any design prompt you
can honestly steer toward risk, payments or abuse.

**What it fixes:** being a generic backend candidate. This is the one answer
where operating experience counts double, because it cannot be revised for.

Real-time fraud detection is the worked example throughout this folder. If your
own production experience is somewhere else — a scheduler, an ingestion
pipeline, a search tier, a billing system — substitute it. The structure below
does not change; only the nouns do.

---

## Why this design, and not a published one

There is no published breakdown for real-time fraud detection, which is exactly
the point. Every other candidate in the pool is reciting Ticketmaster, and the
interviewer has heard that answer eleven times this quarter, including the bit
where the candidate pauses in the same place. You would be describing a system
you have operated, with numbers that came off your own dashboards, in the
vocabulary the interviewer already uses.

Prepared to the standard of a Hello Interview write-up, this becomes the
strongest forty minutes in your loop, and it is portable: it fits "tell me about
something you built", it fits a behavioural round on a difficult trade-off, and
it fits a straight design prompt about payments, abuse, rate limiting or
anything with a decision in the request path.

Two warnings before you start. First, it only works if it is genuinely yours —
the moment you present a system you have read about as one you ran, you are one
follow-up away from a very bad ten minutes, so the parts you did not build are
parts you say you did not build. Second, this is a design document you write
once and rehearse, not a story you improvise. Improvised, it comes out as a tour
of your service; rehearsed, it comes out as a design.

Build it once and it pays for itself in every loop.

## Before anything else, go and get the numbers

Five numbers carry this whole answer, and none of them can be guessed. Block out
an afternoon and dig them out of your own dashboards. That task is worth more
than a week of anything else on this sheet, because an answer with real numbers
in it cannot be given by anyone else and an answer with invented numbers falls
apart the moment someone multiplies two of them together.

| Number | Where to find it |
|---|---|
| Events per second on the scoring path, average and peak | Service request-rate dashboard. Take the peak from a promotion or a weekend dinner rush, not a Tuesday afternoon. |
| Rules live in production right now | The rule config itself. Count them, and note how many were added in the last quarter — the growth rate is a more interesting number than the total. |
| p99 latency of a scoring call, and the budget you hold yourself to | Service latency dashboard. Get p50 and p99, and get the breakdown by stage if your tracing gives you one. |
| Value of fraud prevented, over a period you can name | Risk or finance reporting. Whatever the team reports upward is the number to use, and say which definition it uses. |
| False positive rate, however your team measures it | Manual review outcomes or the chargeback reconciliation. Note the measurement method, because the interviewer will ask how you know. |

Write them into [design-doc-template.md](design-doc-template.md) as you find
them. Until then, that file has blanks in it, and blanks are correct — a
template with plausible-looking invented metrics is worse than useless, because
under pressure you will repeat one in an interview and then have to defend it.

If a number genuinely is not available to you, say that out loud in the
interview and give the shape instead: "I don't have the exact figure to hand,
it was in the low thousands per second at peak." Nobody minds. What they mind is
precision that dissolves on contact.

---

# The build-out, under the delivery framework

The same clock as [hld/00-framework](../00-framework/), run against this
problem. Rehearse to these boundaries, because the failure mode here is not
running out of things to say — it is loving your own system too much and
arriving at minute thirty with the interesting parts still unsaid.

## 0–5 · Requirements

Four, and they are the four the rest of the design falls out of. State them as
constraints, not features.

- **Score a transaction in under 100ms on the synchronous path.** This is a
  budget, not an aspiration: the scoring call sits inside checkout, so every
  millisecond you spend is a millisecond added to a user waiting to pay. Say
  what happens when you blow the budget, because that answer is a design
  decision and not an error case.
- **Catch slow-burn patterns on the asynchronous path.** Some fraud is not
  visible in a single transaction and never will be. It only appears across
  hours or days of history, and there is no version of the synchronous path that
  can see it.
- **Rules changeable by analysts without a deploy.** The people who spot a new
  fraud pattern are not the people who can ship code, and the gap between "we
  noticed" and "we blocked it" is measured in money.
- **Every decision auditable.** For a customer who was wrongly blocked, for a
  chargeback dispute, and for the analyst asking six weeks later why this
  transaction went through. You must be able to say which rules ran, what each
  one returned, and what the inputs were at the time.

Then scope out loud. Say you are not covering account takeover detection,
manual review tooling, or the chargeback dispute workflow with the payment
network unless they want it. Scoping down is a senior move.

Two non-functional ones to state yourself, since nobody volunteers them: the
scoring service is in the revenue path, so its availability requirement is the
checkout's availability requirement, and a fault has to degrade to a decision
rather than to an error; and rule changes need to be reversible in seconds,
because the fastest way to lose a lot of money in this system is a bad rule
rolled out confidently.

## 5–10 · Estimation

Only compute what changes a decision. Four numbers do that here, and each one
starts from a figure you looked up rather than one you invented. Write the
arithmetic out, do not carry it in your head.

- **Feature reads per second.** Peak events per second × features read per
  scoring call. This is the number that decides whether the feature store is a
  single Redis or a cluster, and it is usually a multiple of the transaction
  rate that surprises people. Blank: `___ eps × ___ features = ___ reads/sec`.
- **Feature store memory.** Distinct entities you keep aggregates for (users,
  devices, cards) × bytes per entity × the window you retain. Then say what the
  TTL is, because unbounded aggregates are how a feature store becomes an
  outage. Blank: `___ entities × ___ bytes × ___ windows = ___ GB`.
- **Kafka volume and retention.** Events per second × average event size ×
  retention. Retention here is not arbitrary: it is however far back you need to
  be able to replay when a model or a rule turns out to be wrong, so derive it
  from the backfill requirement rather than picking a round number of days.
- **Flink state.** Keyed state is driven by key cardinality, not by throughput —
  distinct keys × state per key. Say which key has the highest cardinality and
  what bounds it, because that is the question that follows.

Eight minutes maximum. Candidates who enjoy this part burn fifteen and lose the
deep dive.

## 10–15 · API and the decision record

Small surface, and it is worth showing because it makes the two paths concrete.
One synchronous endpoint that takes a transaction and returns a decision —
allow, review or block — along with a reason and a decision ID. One event
stream that the same transaction is published to regardless of the decision. One
read path for the audit record.

The entity that matters is the **decision record**: the transaction ID, the
rules that ran, what each returned, the feature values as they were at scoring
time, the final decision, and the model version. Storing the feature values as
they were is the part people miss, and it is what makes the record actually
useful six weeks later — recomputing a velocity counter today tells you nothing
about what the system saw then.

Access patterns: look up a decision by transaction ID, list decisions for a user
over a window, and scan by rule for "everything this rule blocked yesterday",
which is the query the false-positive investigation always starts with.

## 15–25 · High-level design: the two paths

This is the spine of the answer. Draw two paths and walk one transaction
through both.

**The synchronous path** sits inside the checkout request. It receives the
transaction, reads precomputed features from the store, runs the rule chain in
cost order, and returns a decision inside the budget. It computes nothing
expensive and it calls nothing it cannot bound. Every dependency has a timeout
that fits inside the budget, and every dependency has a stated behaviour on
timeout.

**The asynchronous path** consumes the same transactions from Kafka and analyses
them in Flink, with windows over hours and days. It does the work that needs
history: velocity across a rolling window, patterns across a device or a card
that only emerge over many transactions, graph-shaped signals where one account
connects to others. Its outputs are of two kinds — aggregates written back into
the feature store so the synchronous path can read them next time, and alerts or
review cases for anything it finds after the fact.

**Explaining why some checks cannot be synchronous is the sharpest part of the
answer**, so do not rush it. There are three separate reasons and they are worth
separating out loud:

1. **Time.** The computation is a windowed aggregate over hours of history. You
   cannot do it in single-digit milliseconds, and you should not try.
2. **Data that has not arrived.** A slow-burn pattern is not present in the
   transaction being scored. No amount of latency budget conjures it up.
3. **Cost.** Some checks are affordable at the rate fraud actually occurs but
   not at the rate transactions occur, and the async path lets you run them on a
   filtered subset.

The pairing to state plainly: the synchronous path decides, the asynchronous
path learns, and the feature store is the only thing they share. That single
sentence is the design.

## 25–40 · Deep dives

Offer these three. They are the ones with real substance, and two of them are
places where your experience shows immediately.

### The feature store

Precomputed aggregates in Redis — velocity per user, per device, per card, over
several windows. **The synchronous path reads, it never computes.** That is the
rule the whole latency budget rests on, and it is worth saying as a rule rather
than as a description, because it tells the interviewer you know where the
budget went.

Points worth making:

- Writes come from the Flink jobs on the async path, so the store is
  deliberately slightly stale. Name the staleness — the lag between an event
  happening and its aggregate being visible — and say why that is acceptable for
  a velocity counter, and where it would not be.
- Every key has a TTL matched to its window. Aggregates without a TTL are the
  most common way this component turns into an incident.
- Key design decides your hot-key exposure. A per-user counter distributes
  nicely; a per-merchant or per-city counter does not, and on a food delivery
  platform there is always one restaurant having a moment.
- The read is a single pipelined round trip for all the features a scoring call
  needs, not one call per feature. Say the number of round trips your budget
  allows.
- What the synchronous path does when the store is unavailable is not an
  afterthought: it is per-check policy, and it belongs in the failure-modes
  section below.

### The rule engine

A chain of responsibility, **ordered by cost, loaded from config**. Cheap
in-memory checks — blacklist, amount threshold — run before the network call to
the ML scorer, so the expensive check only ever sees traffic that survived
everything else. That ordering is where most of your latency budget is actually
won.

The operational parts are what make this sound like a system rather than a
pattern:

- Each handler records which rule fired and what it returned, which is what
  makes a false positive debuggable six weeks later without a deploy.
- The chain is loaded from config, so analysts reorder it, add to it and disable
  parts of it without shipping code — which is requirement three, delivered.
- A check that cannot reach its dependency fails open or closed **explicitly**,
  and which one it is depends on the check rather than on a global setting.
- The decision is not a boolean. Allow, review and block are three different
  outcomes, and review is the one that makes the false-positive trade-off
  survivable, because a human glance is far cheaper than a wrongly rejected
  customer.

### The feedback loop

Chargebacks and manual review outcomes flow back as labels. Both are delayed —
a chargeback can arrive weeks or months after the transaction — so the training
set for any given day is not complete until well after that day, and any
evaluation you do before then is measuring an incomplete picture. Say that;
almost nobody does.

New rules and new models go into **shadow mode** first: they run on live
traffic, their decisions are recorded, and nothing is blocked. You compare what
they would have done against what actually happened, then promote. This is the
mechanism that lets analysts move fast without letting them cost you a day of
revenue, and it is the answer to half the follow-ups in
[follow-ups.md](follow-ups.md).

The trap in the labels is worth naming: reviews and chargebacks only exist for
transactions you allowed. You never find out what the blocked ones would have
done, so your labelled data is biased by your own past decisions, and the model
trained on it inherits that bias.

## The trade-off to name

False positives cost real revenue and real customers. False negatives cost
fraud losses. **You are tuning a threshold between two business costs, not
toward an abstract accuracy number.** Very few candidates frame it that way, and
the ones who do sound like they have sat in the meeting where it was argued
about.

Take it further than the framing, because the follow-up is always "so how do you
pick it": the two costs are not symmetric and they are not stable. A blocked
genuine customer at checkout may never come back, so the cost is not the value
of that order; the cost of a missed fraud is the chargeback plus the goods. And
the ratio moves — during a promotion, both the volume and the mix of fraud
change, so a threshold tuned in a quiet week is the wrong threshold on the
biggest day of the quarter.

Which means the honest answer to "what accuracy do you get" is that accuracy is
the wrong measure, and you would rather talk about what each kind of error costs
and who owns that decision. It is a business decision that the risk team makes
and engineering implements, and saying so is not a dodge — it is the correct
division of responsibility, and interviewers at senior level recognise it.

## 40–45 · Failure modes and bottlenecks

Protect these five minutes; this section is where the level gets confirmed. The
framing that works: this service sits in the revenue path, so every failure has
to resolve to a decision. Returning an error is not available to you, because an
error at checkout is a lost order either way.

**The feature store is unavailable.** Per-check policy, not a global switch.
Checks whose features are missing degrade to a documented behaviour, and the
chain still returns a decision inside the budget. Say what the degraded rule set
is and roughly what proportion of your detection you keep in that mode — and if
you have never tested it, say that too, because it is the honest answer and the
next sentence is what you would do about it.

**The ML scorer is slow or down.** It is last in the chain and it has the
tightest timeout, so the failure is contained by design. Whether you fail open
or closed is a business call about what your remaining rules catch.

**A bad rule is pushed.** The most likely serious incident in this system, and
the fastest one. Cover config validation before load, shadow mode as the default
for anything new, a staged rollout by percentage of traffic, an alert on block
rate rather than on errors, and a kill switch that is a config change rather than
a deploy. Time-to-revert is the metric that matters, so name it.

**Kafka lag on the async path.** The system does not fall over; it goes blind
slowly. Aggregates in the feature store go stale, the synchronous path carries on
scoring against old numbers, and nothing errors. That silence is exactly why lag
needs to be a first-class alert on the freshness of the features rather than on
the health of the consumer.

**Flink state growth and checkpoint failures.** Unbounded key cardinality is the
usual cause, checkpoint duration is the early warning, and TTL on state is the
fix you should have applied before it happened.

**Hot keys.** One restaurant, one device, one card in a promotion. This hits the
feature store and the Flink key space at the same time, and it is your Kafka
story from [hld/09-technology-deep-dives](../09-technology-deep-dives/) reappearing in
a different room.

**The audit write fails.** You have already made a decision and the customer is
waiting, so the decision cannot block on durably recording it — but the record
cannot be lost either, because it is a compliance obligation. Publishing the
decision record to a log and consuming it into storage is the shape here, and
being clear about the window where a record could be lost is better than
claiming there isn't one.

**What breaks at ten times the load.** Take the components in order: partition
count on Kafka caps consumer parallelism, so it has to be sized ahead rather
than reactively; the feature store is single-threaded per shard, so a hot key is
a ceiling that adding memory does not raise; Flink rescaling is bounded by state
redistribution; the rule chain's cost per transaction grows with the number of
rules, and rules only ever get added. Then the answer that separates you: **the
review queue does not scale.** Ten times the traffic at the same review rate is
ten times the analysts, which nobody is hiring, so at that load the threshold
itself has to move and the trade-off you named earlier gets renegotiated. That
is a bottleneck an engineer who has only read about this system will not think
of.

---

## The same story at two zoom levels

The rule engine here and the chain of responsibility in
[lld/07-chain-of-responsibility](../../lld/07-chain-of-responsibility/) are the
same design described at different altitudes, and that is unusual enough to be
memorable. The LLD folder implements exactly this: a risk chain of velocity,
device fingerprint, blacklist, amount threshold and ML score checks, ordered by
cost, loaded from config, with per-check fail-open or fail-closed policy and a
feature store interface behind the precomputed aggregates.

Read the two together and rehearse the crossover, because it gives you something
almost nobody has — one system you can zoom into until you are writing the
`handle()` method and zoom out of until you are drawing Kafka and Flink on a
whiteboard, without ever changing story. If your LLD round asks for chain of
responsibility, you can answer with the same domain you designed in your HLD
round; if your HLD round asks how analysts change rules, you can drop straight
into the interface that makes it possible.

The joins to point at when you do it: the chain's cost ordering is the latency
budget from the HLD; the feature store interface in the code is the Redis
component on the diagram; per-check fail-open policy is the degraded mode in the
failure section; and the four-way decision — allow, block, review, continue — is
what makes the false-positive trade-off implementable rather than just
describable.

## The files here

- [design-doc-template.md](design-doc-template.md) — the document to actually
  write, section by section, with prompts and blanks where your numbers go.
- [follow-ups.md](follow-ups.md) — the hardest questions that come after the
  design, and what a strong answer needs.
- [rehearsal-log.md](rehearsal-log.md) — three timed runs, tracked. The design
  is not finished until this table is full.

Do them in that order. The document first, because you cannot rehearse what you
have not written; then the follow-ups, because they will send you back to edit
the document; then the runs.

## Read

- [Pattern — counting and aggregation (step 07)](https://www.hellointerview.com/learn/system-design/patterns/scaling-writes)
- [Ad click aggregator, as a structural template](https://www.hellointerview.com/learn/system-design/problem-breakdowns/ad-click-aggregator)
- [Payment system, for the vocabulary](https://www.hellointerview.com/learn/system-design/problem-breakdowns/payment-system)

## Practice

| Task | What to watch for |
|---|---|
| [Write the full design document](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery) **(core)** | Requirements through failure modes, with your real numbers in it. |
| [Present it out loud, timed, three times](https://www.hellointerview.com/learn/system-design/in-a-hurry/delivery) **(core)** | Record the third. Fix whatever makes you wince. |
| [Prepare the three hardest follow-ups](https://www.hellointerview.com/learn/system-design/deep-dives/flink) **(core)** | How do you handle a rule that starts false-positiving in production? How do you backfill after a bad model? What breaks at 10x? |
