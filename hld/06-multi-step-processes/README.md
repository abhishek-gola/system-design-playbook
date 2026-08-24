# Multi-step processes

**The signal:** a flow touches three or more services, and a failure in step four
means steps one to three have to be undone or retried.

**What it fixes:** pretending you can have a distributed transaction. You can't.

Payments, order fulfilment, refunds, onboarding. If your background is delivery
or commerce, this is the pattern your day job maps onto most directly, and the
one where you can speak from experience instead of from a diagram.

---

## Worked: a payment system

Reserve inventory, authorise the card, capture, update the order, notify the
user, trigger fulfilment. Six local transactions across five services, and any
one can fail or time out ambiguously. The code in this folder implements the
first five, which is enough to show every mechanism.

```
  reserve-inventory ──► authorise-card ──► capture-funds ──► update-order ──► notify
        │                     │                  │                │
     release              void auth           refund         cancel order      (nothing —
                                                                                you cannot
                                                                                unsend an email)
```

Notice the asymmetry. The compensations get more expensive and less complete as
you move right. Releasing a reservation costs nothing. Refunding a capture costs
you fees and puts two lines on the customer's statement. Unsending a notification
is not a thing. That asymmetry is the reason irreversible steps go last, and
saying so unprompted is worth a lot.

## Saga: local transactions plus compensations

Each step commits locally and has a compensating action that undoes it. There is
no rollback across the whole thing — there is a sequence of forward steps and a
sequence of backward ones.

The word to be careful with is rollback. A rollback un-happens a change and
leaves no trace. A compensation is a **new** change that makes up for the old
one, and the old one is still in the ledger. A saga does not give you atomicity;
it gives you a system that ends in a state you chose rather than a state you
discovered.

Compensations run in reverse, and that is not decoration. Later steps depend on
earlier ones, so undoing forwards would try to void an authorisation that has
already been captured.

## Orchestration or choreography

| | Choreography | Orchestration |
|---|---|---|
| How it works | each service listens for events and reacts | a coordinator drives the steps and holds the state |
| Coupling | services know about events, not about each other | services know nothing; the coordinator knows everything |
| Where the flow lives | in everyone's heads, and in five codebases | in one place you can read |
| Debugging a stuck order | read five services' logs and infer | read one row |
| Adding a step | edit whichever service should react, and hope | edit the step list |
| Cost | no coordinator, and no single point of failure | one more service to run and make highly available |

Have a preference and give a reason. For payments, orchestration usually wins,
because when a customer rings up you need to answer "where is this transaction
right now" in under a second, and the coordinator's state row is that answer.
Choreography is a better fit when the reactions are genuinely independent and
nobody needs a single view — a user signing up and three services each doing
their own unrelated setup.

The version to avoid is choreography that has quietly become orchestration:
services listening for each other's events in a fixed order that only works if
you squint. That is an orchestrator with no owner, no state, and no name.

## The outbox pattern — name it

Writing to your database and publishing to Kafka cannot be atomic. Whichever you
do first, the process can die in the gap. Commit then publish and you lose the
event: the reservation is real and nobody downstream learns about it. Publish
then commit and you get the opposite, an event describing something that never
happened, which is worse because it is confidently wrong.

So write the event to an `outbox` table in the same local transaction as the
business change, and have a relay — or change data capture on the table — publish
it afterwards.

```sql
BEGIN;
  UPDATE inventory SET units = units - 1 WHERE sku = 'SKU-42';
  INSERT INTO outbox (event_type, payload) VALUES ('inventory.reserved', '...');
COMMIT;
```

Now the event is guaranteed to be published exactly when the business change
committed, and never otherwise. The relay can still crash between publishing and
marking the row done, so the event may go out twice — which is fine, because
that was always going to be true and the consumer deduplicates.

Polling relay or CDC: CDC adds no query load and no polling lag, which is why
Debezium exists. A poller is easier to explain and you can debug it with a
`SELECT`. I would start with the poller and move to CDC when the lag or the load
starts showing up on a graph.

## Exactly-once is a transport lie

What you actually get is at-least-once delivery plus idempotent consumers. Three
mechanisms, and you should offer all three:

**Idempotency keys on every mutating endpoint.** The key must be *derived*, not
generated — from the order and the step, so a retry produces the same key. A
random key per attempt makes every retry a fresh charge, which is precisely the
bug the mechanism exists to prevent. This is the single most common mistake in
this area and it is easy to make.

**Dedup on message ID at the consumer.** Stable ids, and a store of what you have
already processed with a retention window long enough to cover your worst
redelivery.

**A reconciliation job.** At scale a small fraction will always end up
inconsistent, and you need a process that finds them rather than a design that
pretends they don't exist. Reconcile against the payment provider's settlement
file as well as your own tables, because the provider is the authority on what
was actually charged.

## The follow-ups, and how to answer them

**"What if the compensation itself fails?"** Retry it with backoff, and if it
still fails, stop and leave the record for reconciliation. Do not keep
compensating the earlier steps — while the money is in an unknown state you do
not want to release the stock as well, because then you have taken a payment you
cannot fulfil. Halting into a flagged state that a human or a repair job picks up
is the correct answer, and the code here does exactly that.

**"How do you know the step failed rather than the response being lost?"** You
don't, and that is the point. A timeout is ambiguous. This is why every mutating
step carries an idempotency key: you retry regardless, and the key makes the
retry free. If the answer still matters, you query the downstream service by your
own idempotency key and let it tell you what it did.

**"Who owns the saga state, and what if the orchestrator dies?"** The
orchestrator persists the step index after every step, so a new instance reads
the row and resumes. That is also the honest moment to say that Temporal or Step
Functions are this class plus durable state and timers, and that writing your own
is reasonable for one workflow and a mistake for twenty.

**"How long can a saga run?"** Longer than you think, and that is what breaks
naive implementations. A refund saga can span days waiting on a provider. Any
design that holds a lock, a connection or an in-memory object for the duration of
the saga is wrong for that reason alone.

## The trade-off to name out loud

A saga trades atomicity for availability, and pays for it with intermediate
states the business has to accept. There will be a window where the money has
been taken and the order is not yet confirmed, and no protocol removes that
window — it only makes it shorter. So the design question is not how to eliminate
it but what the customer sees while it is open, and how quickly reconciliation
closes it. Answer that and you sound like someone who has been on call for one of
these.

## The common mistake

Reaching for two-phase commit. It exists, it gives you atomicity, and it does it
by holding locks across services while a coordinator decides — which means one
slow participant blocks everybody, and a coordinator crash leaves locks held with
nobody to release them. That is why nobody runs it across service boundaries, and
saying so briefly is better than not mentioning it at all.

The second mistake is treating the happy path as the design. The saga is the
compensations. If you sketch five boxes with arrows and no backward path, you
have drawn a workflow, not a saga.

## Its in-process shadow

A command with an undo is a saga step without the network:
[lld/13-command](../../lld/13-command/). Same interface, same reverse-order
unwinding. What the network adds is ambiguity — in one process, if `execute`
returned you know it ran, and across a network you don't. Every extra mechanism
here exists to cope with that one difference.

The other neighbour is [hld/05-contention](../05-contention/). Its hold-and-confirm
flow ends with a payment that succeeded and a confirm that failed, and the
idempotency key and reconciliation job that repair it are built here.

---

## Run it

```
./run.sh hld/06-multi-step-processes
```

Five acts. A successful run of the whole flow, with the outbox rows it produced
and the relay publishing them; a run that fails at step four with the
compensations printed in reverse; the outbox compared against publishing directly,
including what is lost when the process dies in the gap; idempotency keys making
two retries of a card authorisation cost one charge, plus a consumer deduplicating
three deliveries of one message; and finally a run where the refund fails too,
leaving money captured against an unconfirmed order, which the reconciliation job
then finds and repairs.

## Practice

| Problem | What to watch for |
|---|---|
| [Design a Payment System](https://www.hellointerview.com/learn/system-design/problem-breakdowns/payment-system) **(core)** | The anchor. Saga, idempotency, reconciliation, and the ledger. |
| [Design Uber](https://www.hellointerview.com/learn/system-design/problem-breakdowns/uber) **(core)** | The ride lifecycle is a long saga with a matching problem bolted on. |
| [Design a Local Delivery Service (GoPuff)](https://www.hellointerview.com/learn/system-design/problem-breakdowns/gopuff) | Inventory, order and fulfilment across warehouses. |

## Read

- [Pattern — multi-step processes](https://www.hellointerview.com/learn/system-design/patterns/multi-step-processes)
- [Idempotency](https://algomaster.io/learn/system-design/idempotency)
- [Read: how Airbnb avoids double payments](https://medium.com/airbnb-engineering/avoiding-double-payments-in-a-distributed-payments-system-2981f6b070bb)
