# The follow-ups

The design gets you to minute thirty. These are what decides the round after
that, because a follow-up is where the interviewer stops checking whether you
can describe a system and starts checking whether you lived in one.

Under each question is what a strong answer needs, not the answer itself. Write
your own, out loud, and keep each one to about ninety seconds spoken. If an
answer runs past two minutes you are narrating rather than answering, and the
interviewer has already stopped listening for the part they asked about.

The three at the top are the ones the sheet names. Prepare those to the point of
fluency; the rest to the point of not being surprised.

---

## The three

### "A rule starts false-positiving in production. What do you do?"

They are asking two things at once: can you handle an incident, and do you
understand that this incident costs money in both directions.

A strong answer has an order to it. Detection comes first — what alerted you,
and whether it was your own monitoring or a complaint from the business, which
is an uncomfortable but honest thing to say if it is true. Then containment
before diagnosis: the rule goes into shadow mode or off entirely, and that has
to be a config change measured in minutes, not a deploy measured in hours. Say
what your actual time-to-revert is.

Then the diagnosis, and the part that shows depth: a rule that was fine and is
now false-positiving usually did not change. The traffic changed, or a feature
it depends on changed, or an upstream aggregate went stale and the rule is
reading a number that no longer means what it did. Distinguishing "the rule is
wrong" from "the rule's input is wrong" is the whole game, and it is why the
decision record stores feature values as they were at scoring time.

Finish on the customers already affected, because engineers forget this half:
the transactions wrongly blocked are recoverable business, and somebody needs a
list of them. Then what you changed so the next rule cannot do this — shadow
mode by default, a block-rate alert per rule rather than in aggregate, a staged
rollout.

### "You shipped a bad model. How do you backfill?"

They want to know whether you have ever had to repair data rather than just
serve it.

Start by separating what can be repaired from what cannot. Decisions already
returned to customers are final; transactions were allowed or blocked and the
world moved on. What you can repair is the derived state — aggregates, features,
review cases, anything downstream that was computed from bad scores — and being
clear about that boundary is the first thing a strong answer does.

Then the mechanics: replay from Kafka, which is why your retention is derived
from the backfill requirement rather than picked as a round number of days. Say
how far back you can actually go, and what happens if the answer is "not far
enough". Cover reprocessing without double-counting, whether you replay into the
live feature store or into a parallel one and swap, and what the async path does
while the backfill is running.

Two details that mark experience. First, event time versus processing time
matters here specifically: a replay must produce the same aggregates it would
have produced the first time, and that only holds if the pipeline was built on
event time. Second, point-in-time correctness — when you rebuild training data,
each row must use the feature values as they were at that moment, not as they
are now, or you have leaked the future into your training set and the new model
will look excellent in evaluation and worse in production.

Finish with the identification problem: knowing which decisions were affected
requires the model version to be on the decision record. If it is not, say so.

### "What breaks at ten times the load?"

Go component by component and be specific about the mechanism, not just the
name. Kafka partition count caps consumer parallelism and has to be sized ahead
of the growth rather than in response to it. The feature store is single-threaded
per shard, so a hot key is a ceiling that more memory does not raise. Flink
rescaling is bounded by redistributing state, so the cost of a rescale grows
with the thing that made you need it. The rule chain's cost per transaction
grows with the number of rules, and rules are only ever added.

Then say which of those you would hit first, because "everything scales
eventually" is not an answer and picking the first bottleneck is the actual
skill.

Land on the one that is not a machine: the review queue does not scale. Ten
times the traffic at the same review rate is ten times the analysts, and nobody
is hiring them, so at that load the threshold moves and the trade-off gets
renegotiated with the business. Very few candidates get to a non-technical
bottleneck, and it is exactly the kind of thinking a senior title is meant to
signal.

---

## The rest, in rough order of likelihood

### "Why not just run the ML model on every transaction and skip the rules?"

Cover cost and latency at your transaction volume, but do not stop there — the
stronger half is explainability. You have to tell a customer, an analyst, or a
regulator why a transaction was blocked, and "the model said 0.93" is not an
answer anyone accepts. Rules also let a human respond to a new pattern within
the hour, which no retraining cycle matches. Concede the real cost of rules
honestly: they accumulate, they interact, and nobody wants to delete one.

### "How do you know the model is still good six months later?"

Cover drift, and be precise about which kind — the traffic changes, or the
fraudsters change in response to you, which is drift with an adversary behind
it. Cover the monitoring that catches it without labels, since labels arrive
late: score distribution, rule fire rates, the mix of decisions. Then what
triggers a retrain and who decides.

### "Your labels arrive weeks or months late. How do you train on them?"

Cover the label delay explicitly and what it does to evaluation — a model
evaluated on recent data is being evaluated on an incomplete label set, and it
will look better than it is. Cover point-in-time correctness when building the
training set. Cover the selection bias: you only learn outcomes for transactions
you allowed, so the model never sees what the blocked ones would have done. If
you do anything about that — a small random allow-through, or using review
outcomes as a proxy — say so; if you do not, say that too, and say what it
costs you.

### "An analyst pushes a rule at two in the morning. What stops it taking down checkout?"

Cover validation of the config before it loads, shadow mode as the default state
for anything new, staged rollout, and the kill switch. Then the guardrail that
matters most: an alert on block rate, per rule, so a rule blocking far more than
its predecessor is caught by monitoring rather than by the business ringing up.
Say who is allowed to push, and whether that is enforced or conventional.

### "Two rules disagree. Who wins?"

Cover precedence and terminal decisions — a block that stops the chain, an
explicit allow that skips the rest — and why that ordering is a policy decision
rather than an implementation detail. Then the operational consequence: because
the chain short-circuits, the decision record shows which rules ran and which
never got the chance, and that matters when you are trying to work out why
something was not caught.

### "How do you test a rule before it goes live?"

Cover replay against historical traffic and what that tells you (how often it
would have fired, and against which known outcomes), then shadow mode on live
traffic and what that adds. Be clear about what neither of them tells you: how
fraudsters respond once the rule is real.

### "The feature store is empty after a failover. What does the system do?"

Cover the cold-start behaviour honestly — a velocity counter that has lost its
history reads as zero, which looks exactly like a well-behaved customer, so an
empty store fails open silently unless you have made it detectable. Cover how
you tell "no activity" from "no data", warm-up from the async path, and whether
you would rather degrade to a reduced rule set for the warm-up window. This is a
good question to admit the limits of what you tested.

### "How do you explain a block to a customer, or to a regulator?"

Cover the decision record, per-rule attribution, and retention. Then the harder
half: the reason has to be expressible in a sentence a support agent can say,
which constrains how opaque any single check is allowed to be. This is where the
rules-versus-model argument returns with a business justification instead of a
technical one.

### "How do you stop the same transaction being scored twice with different results?"

Cover idempotency on the scoring call keyed by transaction ID, what you return
on a retry, and why the answer must be the previous decision rather than a fresh
one. Then the subtlety: the features have moved on between the two calls, so a
fresh evaluation could legitimately differ, and a payment flow that retries must
not be able to shop for a better answer.

### "What is your p99 actually made of?"

Cover the breakdown by stage — the feature read, the cheap checks, the model
call, the audit write — with real numbers if you have tracing. Then say which
stage owns the tail and why, because p99 is nearly always one dependency rather
than everything being slightly slow. If your audit write is on the synchronous
path, expect to defend that.

### "How would you replace the rule engine while it is running?"

Cover running both, comparing decisions on live traffic, and migrating rule by
rule rather than in one cut. The point to make is that this is the same shadow
mode machinery you built for new rules, applied to a whole component — a system
that can safely introduce one rule can safely introduce a replacement engine,
and noticing that is worth saying.

### "Was it worth it? Prove the system pays for itself."

Cover value prevented against the cost of running it, and then the honest
correction: the true comparison also includes revenue lost to false positives,
which is a number risk teams are much less keen to publish. If you know it, use
it. If you do not, say that measuring the cost of your own false positives is
harder than measuring the fraud you stopped, and that the asymmetry is itself
worth being aware of when the threshold gets argued about.
