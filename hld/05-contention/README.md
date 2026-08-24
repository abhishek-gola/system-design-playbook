# Contention

**The signal:** seats, inventory, auction bids, wallet balance, coupon codes —
anything where two people can want the same unit.

**What it fixes:** overselling. And the naive fix, a distributed lock held
across a payment call, which is worse.

This is the pattern most likely to appear in a payments or commerce interview,
and the one candidates most often fumble. The fumble is rarely ignorance of
locking. It is reaching for a distributed lock in minute two and then being
unable to answer what happens when the payment provider takes eight seconds.

---

## Worked: Ticketmaster

Without coordination, two users both see seat 14A available and both buy it.
The check and the claim must be atomic. That sentence is the entire problem, and
everything below is a different price you can pay to make it true.

```
browse seats ──► POST /hold  ──► seat row moves FREE ──► HELD (expiry = now + 10m)
                                        │
                                        ├── user pays (slow, external, may fail)
                                        │
                    POST /confirm ──► HELD ──► SOLD          (fencing token checked)
                                        │
                    sweeper / TTL ───► HELD ──► FREE          (user abandoned checkout)
```

## The ladder, cheapest first

Walk it in this order out loud. Starting at the top makes you sound like you
have read about distributed systems; starting at the bottom makes you sound like
you have run one.

| Rung | Mechanism | Use it when | What it costs |
|---|---|---|---|
| Single-row transaction | `SELECT ... FOR UPDATE`, or a conditional `UPDATE` | all the contended state lives in one database | nothing beyond what you already run |
| Optimistic concurrency | a version column, checked via the affected-row count | conflicts are rare | wasted work on every conflict, and it grows with contention |
| Distributed lock | Redis or ZooKeeper | the state genuinely spans systems | latency, plus a new failure mode you now own |
| Serialise by partition | route every operation for one event to one consumer | you already have a log, and per-key ordering is enough | a throughput ceiling per event, and rebalances to reason about |

The bottom rung deserves more air time than it usually gets. A conditional
update — `UPDATE seats SET owner = ? WHERE id = ? AND owner IS NULL` — is
atomic, is one round trip, needs no extra infrastructure, and is correct. If a
single row can hold the contended state, that is the answer and you should say
so before you say the word Redis.

Serialising by partition is the one worth naming when the volume is absurd. All
operations for event 4471 hash to one Kafka partition, one consumer owns that
partition, and it processes claims one at a time. There is no lock because there
is no concurrency. The cost is that a single hot event cannot go faster than one
consumer, which is exactly the trade an auction or a ticket drop is usually happy
to make.

## Optimistic or pessimistic, and how to choose

| | Optimistic | Pessimistic |
|---|---|---|
| Cost when uncontended | almost nothing | a lock acquisition on every request |
| Cost when contended | retries, and they get worse as load rises | queueing, which is at least bounded |
| Failure mode | livelock — everyone retries, nobody finishes | a stuck lock holder blocks everybody |
| Good fit | profile edits, warehouse counts, anything where two writers rarely meet | the last seat of a sold-out show |

The rule I would give: optimistic when conflicts are the exception, pessimistic
when conflicts are the point. A ticket drop is contention by design, so a scheme
whose cost rises with contention is the wrong scheme.

## The design that actually ships

A hold with a TTL. Reserve the seat, get ten minutes, take payment, then
confirm. State this explicitly, because it is the key insight of the whole
pattern: it converts a lock held across a slow external call into a database row
with an expiry. Nothing is blocked while the user finds their card. Abandoned
holds are swept by a background job or expire naturally via a TTL index.

Prefer both mechanisms rather than one. A TTL index the database enforces is the
correctness story; the sweeper is the operational one, because you want a job you
can run on demand and whose lag you can graph. A sweeper falling behind should
cost you a few unsellable seats for a minute, never a double sale.

## Two follow-ups that come every time

**Payment succeeds, confirm fails.** The money moved and your database does not
know. You need three things and should offer all three unprompted: an idempotency
key on the payment so the retry does not charge twice, a reconciliation job that
finds paid-but-unconfirmed holds and repairs them, and a clear rule about what
the user sees in the meantime. The honest answer to the last one is usually a
pending state with a message, not a spinner that lies. Both of the first two are
built and runnable in [hld/06-multi-step-processes](../06-multi-step-processes/).

**Is your distributed lock actually safe?** If they push here they want fencing
tokens. The argument is short: a lock with a timeout assumes the holder cannot be
paused for longer than the timeout, and that assumption is false. A garbage
collection pause, a hypervisor freezing the VM, a network partition — any of
these can stall a client past its own lease. It wakes up believing it holds the
lock, and it is wrong. No amount of TTL tuning fixes this, because the stall can
always be longer than the TTL.

The fix is a monotonically increasing number handed out with the lock. The client
presents it on write, and the resource refuses anything that is not the current
one. The stalled client's token is stale, so its write bounces. Read Kleppmann's
piece once; being able to reference it changes the tone of the conversation.

## The trade-off to name out loud

Holds trade inventory utilisation for correctness. Ten thousand people holding
seats they will not buy is ten thousand seats nobody else can reach for ten
minutes, and on a hot drop that is most of the venue. So the TTL is a product
decision, not a technical one: shorter windows sell more seats and lose more
carts to timeouts. Say the number and say what would make you change it. Two
minutes for a flash sale, ten for a normal checkout, longer if the payment method
is slow by nature.

## The common mistake

Three, and interviewers watch for each.

The first is holding a lock across the payment call. It looks correct and it
turns your payment provider's bad afternoon into a full outage.

The second is running the conditional update and never reading the affected-row
count. Zero rows affected is not an exception. Nothing throws, the code carries
on as though it won, and you have written overselling with extra ceremony.

The third is answering "use a distributed lock" to a problem where all the state
is in one row of one database. It is a heavier tool with a worse failure mode
chosen for no gain, and it reads as pattern-matching rather than thinking.

## Its single-process twin

The same seat-booking problem inside one JVM is [lld/10-concurrency](../../lld/10-concurrency/).
Worth reading the two together, because the shape is identical and only the
enforcement point moves: `synchronized` becomes a row lock, an `AtomicReference`
compare-and-set becomes a conditional `UPDATE`, and a `ReentrantLock` with a
timeout becomes a lease with a fencing token. What does not survive the move is
anything that relied on shared memory, and noticing which of your instincts those
are is the lesson.

---

## Run it

```
./run.sh hld/05-contention
```

Five acts. Two hundred rounds of eight buyers racing for one seat with no
coordination, reported as how many rounds oversold; the same experiment with
`putIfAbsent` doing the check and the act together; a version column with three
buyers reading the same version and two of them having to redo their work; two
group bookings deadlocking on a pair of seats and then the same two succeeding
once the locks are taken in sorted order; and finally the hold-and-confirm flow
with a manual clock, a sweeper releasing an abandoned hold, and a stalled client
being refused by its stale fencing token.

## Practice

| Problem | What to watch for |
|---|---|
| [Design Ticketmaster](https://www.hellointerview.com/learn/system-design/problem-breakdowns/ticketmaster) **(core)** | The anchor. Holds, expiry, the queue for high-demand drops. |
| [Design an Online Auction](https://www.hellointerview.com/learn/system-design/problem-breakdowns/online-auction) **(core)** | Bids arriving faster than you can serialise them. Batching in time windows is the escape hatch. |
| [Design Robinhood](https://www.hellointerview.com/learn/system-design/problem-breakdowns/robinhood) | Order matching, where correctness under contention is the entire product. |

## Read

- [Pattern — dealing with contention](https://www.hellointerview.com/learn/system-design/patterns/dealing-with-contention)
- [How to do distributed locking (Kleppmann)](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)
- [Read: Shopify inventory reservations](https://www.hellointerview.com/learn/system-design/in-the-wild/shopify-inventory-reservations)
