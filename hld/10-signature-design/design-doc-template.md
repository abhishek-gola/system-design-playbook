# Real-time fraud detection — design document

> **How to use this.** Write it out in full, in prose, as though it were a
> document a colleague will read without you in the room. Not bullet points you
> intend to expand while speaking — the expanding is the part that goes wrong
> under pressure. Every `[__ ]` is a blank you fill from your own dashboards,
> your own config, or your own postmortems. Leave a blank as a blank until you
> have looked it up. An invented number in here will come out of your mouth in
> an interview and you will not be able to defend it.
>
> Target length: something you can present in forty minutes with five minutes
> spare. If a section runs longer than the time you have for it, cut it here
> rather than discovering the problem on the clock.

---

## 1. What this system does

One paragraph, no jargon, the version you would give a product manager. What
decision does it make, for whom, at what moment, and what happens as a result of
each outcome.

*Then one sentence on your part in it: what you built, what you operated, what
you inherited. Be exact. This sentence protects everything below it.*


## 2. Requirements

Four functional requirements, written as constraints:

1. Score a transaction in under 100ms on the synchronous path.
2. Catch slow-burn patterns on the asynchronous path.
3. Rules changeable by analysts without a deploy.
4. Every decision auditable.

*Add anything else that is genuinely a requirement of your system, and delete
nothing without a reason.*

Non-functional, as numbers:

- Availability the scoring service is held to: `[__ get from the SLO or the
  service dashboard ]`
- Latency budget you hold yourself to, versus what you actually see:
  `[__ target ]` versus `[__ measured p99 ]`
- How long a rule change takes to take effect: `[__ from the deploy or config
  pipeline ]`
- Retention on the audit record: `[__ from the storage config or the compliance
  requirement ]`

Explicitly out of scope, and say this out loud in the interview:


## 3. Estimation

Show the arithmetic. Compute only what changes a decision.

| Quantity | Working | Where the input came from |
|---|---|---|
| Events per second, average | `[__ ]` | Service request-rate dashboard |
| Events per second, peak | `[__ ]` | Take a promotion or a weekend dinner peak, not a quiet Tuesday |
| Features read per scoring call | `[__ ]` | Count them in the rule config |
| Feature store reads/sec | peak eps × features per call = `[__ ]` | Derived |
| Feature store memory | entities × bytes × windows = `[__ ]` | Redis `INFO memory`, or derive and then check against it |
| Kafka volume | eps × event size × retention = `[__ ]` | Topic config and broker metrics |
| Flink keyed state | distinct keys × state per key = `[__ ]` | Checkpoint size on the job dashboard |
| Rules live today | `[__ ]` | Count the rule config. Note how many were added last quarter |
| Value of fraud prevented | `[__ over what period, on what definition ]` | Risk or finance reporting |
| False positive rate | `[__ and how it is measured ]` | Review outcomes or chargeback reconciliation |

*The two numbers to be most careful with are the last two. Know the definition
behind each, because "how do you measure that?" is the immediate follow-up and a
shrug there undoes the number.*


## 4. API and the decision record

The endpoints — four to six at most:


The decision record, field by field. Include the rules that ran, what each
returned, the feature values **as they were at scoring time**, the final
decision, and the model version:


Access patterns for it, and what storage that implies:

- by transaction ID
- by user over a window
- by rule, for "everything this rule blocked yesterday"


## 5. High-level design — the two paths

Draw the diagram. Then write the walkthrough of one transaction end to end in
prose, because the prose is what you will actually say.

### The synchronous path

What it does, what it is allowed to call, and the timeout on each dependency:


Where the 100ms goes. Break the budget down by stage and put the measured
numbers next to the intended ones: `[__ get from tracing if you have it ]`


### The asynchronous path

What runs in Flink, over what windows, keyed by what:


What it writes back, and what it raises as a case:


### Why some checks cannot be synchronous

The three reasons, in your own words, with a concrete example of each from your
own system:

1. Time —
2. Data that has not arrived —
3. Cost —


## 6. Feature store

What is precomputed, keyed how, with what TTL:


How stale it is, and why that is acceptable: `[__ measure the lag between event
and aggregate visibility ]`


How many round trips a scoring call makes, and what happens when the store is
unavailable, per check:


## 7. Rule engine

The chain, in cost order, with the actual checks from your config:


How it is loaded and changed, and what an analyst can and cannot do without an
engineer:


What each check does when its dependency is unreachable — fail open or fail
closed, per check, with the reason:


The decision types, and what review buys you that a boolean does not:


*Cross-reference: [lld/07-chain-of-responsibility](../../lld/07-chain-of-responsibility/)
is this component at code altitude. Keep the two consistent — if you change the
story here, change it there.*


## 8. Feedback loop

Where labels come from and how delayed each source is: `[__ typical chargeback
delay for your payment mix ]`


How a new rule or model is introduced — shadow mode, what you compare, and what
promotion requires:


The bias in the labels — you only learn the outcome of transactions you allowed
— and what, if anything, you do about it:


## 9. The trade-off

Write this section properly; it is the one they remember.

What a false positive costs, in your business, beyond the value of the order:


What a false negative costs:


Who owns the threshold, how it is set, and when it moves:


Why accuracy is the wrong headline number here:


## 10. Failure modes and bottlenecks

One paragraph each. The framing: this service is in the revenue path, so every
failure has to resolve to a decision rather than to an error.

- Feature store unavailable —
- ML scorer slow or down —
- A bad rule pushed to production, and time to revert —
- Kafka lag on the async path, and how you notice —
- Flink state growth and checkpoint failure —
- Hot keys —
- Audit write failure —

**What breaks at ten times the load**, component by component, ending with the
one that is not a machine: the review queue and the analysts behind it.


## 11. What I would do differently

Two or three things, honestly. This section is optional in the document and
essential in the interview, because "what would you change" is asked almost
every time and the candidates who have thought about it in advance sound
completely different from the ones improvising regret.
