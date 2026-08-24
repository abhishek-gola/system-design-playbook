# Work that outlives the request

**The signal:** the job takes longer than a user should wait, or longer than a
request should hold a thread.

**What it fixes:** web servers blocked on work they shouldn't be doing, and
timeouts that lose the job entirely.

This is the pattern behind uploads, transcoding, report generation, crawling and
every batch job you have ever written. The shape is easy and the failure
handling is the interview.

---

## Worked: YouTube upload and transcode

The shape is always the same. Accept, persist, enqueue, return a job ID
immediately. Workers pull and process. The client polls a status endpoint or
receives a webhook.

```
POST /videos        → 202 Accepted, { jobId }
GET  /jobs/{jobId}  → { state: PROCESSING, progress: 0.4 }
```

Two details in that sketch are worth defending. The 202 rather than a 200 is a
small thing that reads well: it says "I have accepted this and I am not finished
with it", which is exactly the truth. And the row goes in the jobs table *before*
the message goes on the queue, because the other order gives you a worker holding
a message for a job that does not exist yet. Queues are fast enough that this
race is not theoretical.

Keep the two apart in your head. The jobs table is the durable record of what the
user asked for and what happened; the queue is a delivery mechanism with its own
retry state. If the queue evaporated you could rebuild it from the table, and
that is a property worth having rather than a coincidence.

Progress reporting is the small follow-up nobody prepares. The worker writes
progress back to the jobs row every few seconds — not every frame, or you have
turned a transcode into a write-heavy workload against your own database.

## Picking the queue, with reasons

| | Reach for it when | What you're accepting |
|---|---|---|
| **SQS** | plain job processing, which is most of the time | retries, visibility timeout and a DLQ out of the box; no ordering, no replay |
| **Kafka** | you need ordering, replay, or several independent consumers of the same stream | a partition-per-consumer model, consumer group rebalances, and real operational weight |
| **Redis** | the job is cheap to regenerate and you want it now | you can lose jobs, and one day you will |

SQS is the default for job processing and saying so quickly is a point in your
favour. Kafka is overkill for a plain work queue, and saying *that* is worth
another one — it is one of the few places where naming the less fashionable tool
signals more experience than naming the fashionable one. Kafka earns its place
when the same events need several independent readers, or when replaying last
Tuesday is a requirement rather than a wish.

The honest catch with Kafka in this shape: there is no per-message visibility
timeout, so a single slow or poisoned message blocks its partition for everyone
behind it. You end up rebuilding retry topics and a DLQ topic by hand, which is
work SQS gives you for free.

## The two things that separate a real answer

### Idempotency

A worker **will** process the same job twice. Not might. The visibility timeout
expires while the worker is still going, the message reappears, another worker
picks it up, and now two workers are transcoding the same video. Nobody did
anything wrong.

Understanding *why* is the part to get right: the queue cannot distinguish a
worker that has died from a worker that is merely slow, because in both cases it
receives nothing. Given that, it has to guess, and the safe guess is to assume
death and redeliver. That guess is what makes the whole thing at-least-once.

So key the output by job ID and make the second run a no-op. In this folder the
handler keeps a `jobId → output` record and checks before it writes. The version
that survives production expresses the same idea as one conditional write —
`INSERT ... ON CONFLICT DO NOTHING`, an `UPDATE` guarded by the current state, a
conditional PUT keyed on the job id — so that the database decides who won rather
than two workers both reading "not done yet" and both proceeding.

The half people skip: this only works if the whole handler is safe to repeat. A
handler that appends to a log, increments a counter or posts a webhook is not
idempotent because it checked a map at the top. Design the write to be repeatable
and the check becomes an optimisation rather than your only defence.

### Failure handling

Four things, and you want all four in the answer:

| | What it is | The reason it exists |
|---|---|---|
| **Exponential backoff** | 1s, 2s, 4s, 8s, capped | a downstream that is failing needs less traffic, not the same traffic faster |
| **Jitter** | randomise within the window | without it every worker that failed during one outage retries at the same instant and knocks the service over again as it recovers |
| **Retry limit** | give up after N deliveries | otherwise a poison message is retried forever and burns a worker slot on every cycle |
| **Dead-letter queue** | where the give-up goes | it keeps the payload, so the failure is inspectable rather than merely logged |

Jitter is the one to mention unprompted. Plain doubling synchronises the herd,
which is how a partial outage becomes a full one. The code here uses equal
jitter — half the delay fixed, half random — which keeps a floor under the delay
while still smearing the retries across a window.

And then say what a human actually does when something lands in the DLQ, because
that is the operational maturity signal and it costs you one sentence: alarm on
DLQ depth greater than zero, a person looks at the payload, and either the bug is
fixed and the message is replayed onto the main queue or the job is marked failed
and the user is told. Both outcomes are fine. A DLQ nobody watches is a folder of
lost work with a reassuring name.

## The visibility timeout, and how to set it

The mechanism is simpler than it sounds. A received message is not removed, it is
hidden — the queue stamps it with a "visible again at" timestamp and hands it
out. Nothing is locked, nothing blocks, and nobody is notified when the lease
expires. The message simply becomes visible again and the next poll finds it.

Setting it is a real trade-off and the interviewer may push:

- **Too short** and healthy workers get their work stolen mid-job. You pay for
  every task twice and the queue looks busier than the workload is.
- **Too long** and a genuinely dead worker's job sits invisible for that whole
  window before anyone retries it. Your p99 becomes the timeout.

The rule that works: set it to a comfortable multiple of the p99 job duration,
then have long-running workers extend the lease as they go — SQS calls this
changing message visibility, and it is how you support a job whose duration you
cannot bound in advance. Extending a lease you still hold is cheap; guessing a
timeout that covers the worst case is not.

## When the job is several steps

Transcode, then thumbnail, then captions, then publish, with each step able to
fail. That is a workflow, not a job, and it is where you name Temporal, Step
Functions or Airflow rather than hand-rolling state in a database column.

The reason to reach for one is not that hand-rolling is impossible — it is that
you will end up writing the same four things badly: durable state per execution,
resumption after a crash halfway through, per-step retry policy, and a way to
answer "where is execution 8817 right now" without reading logs. That is a
workflow engine, and building it accidentally is worse than adopting one on
purpose.

Where the failure means undoing earlier steps rather than retrying the current
one, you are in saga territory instead — see
[hld/06-multi-step-processes](../06-multi-step-processes/).

## The follow-ups, and how to answer them

| They ask | The answer |
|---|---|
| "How does the client know when it's done?" | Polling with a backoff is the honest default. A webhook is nicer and adds retry, signing and delivery-failure handling as your problem. WebSocket or SSE if the client is already holding a connection — see [hld/03-realtime-updates](../03-realtime-updates/). |
| "How do you scale the workers?" | Autoscale on queue depth or oldest-message age, not on worker CPU. Queue depth is the signal that leads; CPU is the signal that lags. |
| "What if one tenant floods the queue?" | Separate queues per priority or per tenant class. A single queue means your biggest customer's bulk import delays everyone's interactive jobs, and no amount of worker scaling fixes head-of-line blocking. |
| "Can you guarantee exactly-once?" | No, and neither can anyone else. What you get is at-least-once delivery plus idempotent handlers, which is indistinguishable from exactly-once from the outside. Say it plainly. |
| "What if the job is cancelled?" | A cancelled flag on the jobs row that the worker checks at step boundaries. You cannot pull a message back out of a queue, so cancellation is cooperative or it is nothing. |

## The trade-off to name out loud

Queue depth versus worker count is a latency-versus-cost dial, and it is worth
naming because it is the one the business actually cares about. A deep queue with
few workers is cheap and slow; a shallow queue with many workers is fast and
mostly idle. Pick based on what the job is for — a user watching a progress bar
needs the second, an overnight report needs the first — and note that the same
system can have both if the queues are separate.

## The common mistake

Describing the happy path in loving detail and then treating retries as an
implementation detail. Accept, enqueue, workers pull, done — that is thirty
seconds of the answer, and if it is most of what you say the conversation stalls.

The second mistake is claiming exactly-once. It is a transport lie, the
interviewer knows it, and the moment you say it they will spend the rest of the
round finding out whether you know it too. Say at-least-once plus idempotent
handlers and you get the same credit for free.

The third is a retry loop with no limit. It sounds robust and it is the specific
mechanism by which one corrupt upload consumes a worker slot forever.

## Where else this shows up in the repo

- [lld/05-observer](../../lld/05-observer/) is this pattern in one process, and
  the mapping is exact: a per-subscriber bounded queue is a consumer's backlog, a
  subscriber that keeps throwing is a poison message, and the overflow policy is
  your retention policy. If you can explain one you can explain the other.
- [hld/03-realtime-updates](../03-realtime-updates/) is how the client finds out
  the job finished without asking every second.
- [hld/06-multi-step-processes](../06-multi-step-processes/) is where this goes
  when the job becomes four jobs that can each fail and have to be undone.

---

## Run it

```
./run.sh hld/04-long-running-tasks
```

Five upload jobs through a queue with a five-second visibility timeout, a retry
limit of three, and exponential backoff with jitter, driven by a hand-cranked
clock so thirty seconds of queue behaviour prints instantly and identically every
run.

One job goes through cleanly. One stalls on its first attempt, overruns its
lease, gets handed to a second worker, and the original worker eventually
finishes and finds the work already done — that is the duplicate delivery, and
the output is written exactly once. One fails twice with something transient and
succeeds on its third attempt, with the backoff visible between them. One is a
corrupt upload that fails every time, exhausts its retry limit and ends up in the
dead-letter queue. The report at the end shows more deliveries than messages
sent, which is at-least-once working correctly rather than a bug.

## Practice

| Problem | What to watch for |
|---|---|
| [Design YouTube](https://www.hellointerview.com/learn/system-design/problem-breakdowns/youtube) **(core)** | The anchor. Upload, transcode pipeline, and delivery. Also covers large blobs. |
| [Design a Distributed Job Scheduler](https://www.hellointerview.com/learn/system-design/problem-breakdowns/job-scheduler) **(core)** | Cron at scale — leader election, missed-run handling, and exactly-once triggering. |
| [Design a Distributed Web Crawler](https://www.hellointerview.com/learn/system-design/problem-breakdowns/web-crawler) | Politeness, deduplication, frontier management, and a very long tail of failures. |

## Read

- [Pattern — long-running tasks](https://www.hellointerview.com/learn/system-design/patterns/long-running-tasks)
- [Message queues](https://algomaster.io/learn/system-design/message-queues)
- [Kafka vs RabbitMQ](https://www.hellointerview.com/blog/kafka-vs-rabbitmq)
