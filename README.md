# System Design Playbook

**A design interview guide you can run.** Low-level and high-level design,
arranged as a practice sheet: every pattern gets a folder with the signal that
tells you to reach for it, a worked example you can run in dependency-free Java,
and three problems to practise on.

![Java](https://img.shields.io/badge/Java-17%2B-orange)
![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen)
![Code](https://img.shields.io/badge/license-MIT-blue)
![Docs](https://img.shields.io/badge/docs-CC%20BY%204.0-blue)

Most design prep is a wall of prose you read once and forget, or a pile of
solutions you copy without ever writing. This is neither. Every pattern here has
a folder: a page that explains when it applies and what an interviewer will push
on, plus dependency-free Java you can run, break, and rewrite from scratch.

```
./run.sh lld/06-state
./run.sh hld/07-aggregation-and-counting
```

---

## Why this one

**You can run most of it.** 27 folders, 200-plus Java files, no Maven, no
Gradle, no dependencies. 23 of the 27 folders ship a runnable `Demo`, and all 23
compile and run clean on OpenJDK 21; the other four are writing exercises with
nothing to run. A watermark either drops a late event or routes it to a side
output, and you can watch it happen instead of reading a claim about it.

That last sentence is the whole pitch, so here it is happening. Real terminal
output from one folder, two sections cut for length and otherwise untouched:

```text
$ ./run.sh hld/07-aggregation-and-counting

==========================================================================
2. Tumbling, sliding and session, over identical events
==========================================================================

Tumbling, one minute. Fixed and non-overlapping, so every click is counted
exactly once and the counts add up to the event total:

   [0:00..1:00)  ######### 9
   [1:00..2:00)  ########## 10
   [2:00..3:00)  # 1
   [5:00..6:00)  ######### 9
   [6:00..7:00)  ######### 9
   [7:00..8:00)  ## 2
   [10:00..11:00)  ######## 8
   [11:00..12:00)  ######### 9
   [12:00..13:00)  ### 3
   total across windows: 60 (equals the 60 events)

   [ sliding windows omitted ]

Session, ninety second inactivity gap. 13 sessions across 4 users:

   user-1     [1:04..1:50 3 clicks]  [5:25..7:12 7 clicks]  [10:13..10:13 1 click]  [12:02..12:02 1 click]
   user-2     [0:23..1:45 6 clicks]  [5:06..6:52 5 clicks]  [10:48..12:08 7 clicks]
   user-3     [0:07..2:01 6 clicks]  [5:11..7:06 5 clicks]  [10:21..10:56 5 clicks]
   user-4     [0:12..1:56 5 clicks]  [5:56..6:16 3 clicks]  [10:07..12:14 6 clicks]

   [ sections 3 and 4 omitted — event vs processing time, watermarks ]

==========================================================================
5. At-least-once versus effectively-once at the sink
==========================================================================

60 records, a checkpoint every 20, and the process is killed after 35.
The last complete checkpoint at that point covers 20 records, so 15 get
replayed from the source. Both sinks see identical input.

   no crash, two-phase-commit sink : total 60   (4 transactions committed, 0 aborted on restore)

   crash, naive sink (increments on every record)
      records in stream 60, replayed after restore 15
      sink total 75, overcounted by 15
      75 direct writes, no transactions

   crash, two-phase-commit sink (commits only on checkpoint completion)
      records in stream 60, replayed after restore 15
      sink total 60, overcounted by 0
      4 transactions committed, 1 aborted on restore
```

Same events, two window types, and the bursts that are invisible in one are
obvious in the other. Then a job killed mid-stream: the naive sink is over by
exactly the fifteen replayed records, the two-phase-commit sink lands on sixty.
None of that is a diagram of a system — it is the system, small enough to read
in an evening.

**It teaches the trigger, not just the pattern.** Knowing what Strategy is has
never been the hard part. Hearing *"should support multiple ways to…"* and
reaching for it under a clock is. Both tracks open with a table that maps the
sentence an interviewer says to the pattern underneath.

**It covers the things prep material skips.** Concurrency inside an LLD answer,
which decides more rounds than any single pattern. The forty-five minute HLD
script, because candidates lose on pacing rather than knowledge. Defending the
technologies on your CV. And a timed-drill folder, because recognising a design
and generating one are different skills and only the second is tested.

**It argues, rather than lists.** Every trade-off comes with a recommendation and
a reason. Where there's no right answer — `addChild` on `Node` or on `Directory`,
orchestration or choreography — it says so, because interviewers know and are
listening for whether you do.

## Who it's for

Backend engineers with a few years of experience preparing for design rounds,
who can already write the code and need to get fast at producing designs under
observation. If you have never written Java, the patterns still transfer; the
code is deliberately plain.

## Start here

```bash
brew install openjdk@21     # if you don't have a JDK; run.sh finds it on its own
./run.sh                    # lists every folder with runnable code
./run.sh lld/02-strategy    # a rate limiter with three swappable algorithms
```

Then pick a track:

- **[lld/](lld/README.md)** — 16 folders. Modelling, SOLID, the eight patterns
  that actually turn up, concurrency, and timed machine-coding drills.
- **[hld/](hld/README.md)** — 11 folders. The delivery framework, seven scaling
  patterns, technology deep dives, and building one signature design.

There's also a browser version of the same sheet with progress tracking, live at
**[abhishek-gola.github.io/system-design-playbook](https://abhishek-gola.github.io/system-design-playbook/)**
— your ticks save locally in the browser.

## If your onsite is in under three weeks

Ignore the week numbers on the track pages. They assume a fourteen-week run, and
reading in order puts you in `lld/00-modelling` when what's about to cost you the
round is pacing and locking. Six folders, in this order:

1. **[hld/00-framework](hld/00-framework/)** — the forty-five minute clock.
   Pacing loses more rounds than knowledge does, and this is the cheapest fix in
   the repo.
2. **[lld/10-concurrency](lld/10-concurrency/)** — the double-booking race,
   reproduced. Any prompt with seats, inventory or a balance is this question
   wearing a costume.
3. **[hld/05-contention](hld/05-contention/)** — the same problem across
   machines. Read it straight after, while the shape is still fresh.
4. **[lld/02-strategy](lld/02-strategy/)** and **[lld/06-state](lld/06-state/)** —
   the two patterns machine-coding rounds are actually built on.
5. **[hld/01-scaling-reads](hld/01-scaling-reads/)** — the default hard part in
   anything read-heavy.

Then the two "picking the pattern" tables, at the top of
[lld/README.md](lld/README.md) and [hld/README.md](hld/README.md). Ten minutes,
and they carry more per minute than anything else here.

Then **three timed drills** — 50 minutes each, blank file, no notes: Rate
Limiter, BookMyShow, Splitwise. Method and scorecard are in
[lld/15-timed-drills](lld/15-timed-drills/). If you run short on evenings, cut
reading before you cut drills; recognising a design and producing one are
different skills and only the second one gets tested.

## What's inside

| Low-level design | | High-level design | |
|---|---|---|---|
| [Modelling](lld/00-modelling/) | relationships before patterns | [The framework](hld/00-framework/) | the 45-minute script |
| [SOLID](lld/01-solid/) | a logger refactored five times | [Scaling reads](hld/01-scaling-reads/) | caches, stampedes, single-flight |
| [Strategy](lld/02-strategy/) | a rate limiter | [Scaling writes](hld/02-scaling-writes/) | shard keys and hot partitions |
| [Factory](lld/03-factory/) | and its two cousins | [Real-time updates](hld/03-realtime-updates/) | connection routing |
| [Builder](lld/04-builder/) | an order that can't be built wrong | [Long-running tasks](hld/04-long-running-tasks/) | queues, idempotency, DLQs |
| [Observer](lld/05-observer/) | with real backpressure | [Contention](hld/05-contention/) | holds, locks, fencing |
| [State](lld/06-state/) | a vending machine | [Multi-step processes](hld/06-multi-step-processes/) | sagas and the outbox |
| [Chain of responsibility](lld/07-chain-of-responsibility/) | a risk pipeline | [Aggregation](hld/07-aggregation-and-counting/) | windows, watermarks, sketches |
| [Decorator](lld/08-decorator/) | ordering changes behaviour | [Blobs, geo, search](hld/08-blobs-geo-search/) | chunking, geohash, inverted index |
| [Adapter](lld/09-adapter/) | two payment gateways | [Technology deep dives](hld/09-technology-deep-dives/) | Kafka, Flink, Redis, DynamoDB |
| [Concurrency](lld/10-concurrency/) | the race, reproduced | [Signature design](hld/10-signature-design/) | one answer nobody else has |
| [Singleton](lld/11-singleton/) · [Composite](lld/12-composite/) · [Command](lld/13-command/) · [The rest](lld/14-remaining-patterns/) · [Timed drills](lld/15-timed-drills/) | | | |

## How to actually use it

Reading a folder takes ten minutes and teaches you almost nothing. The sequence
that works:

1. Read the README. Close it.
2. Write the pattern from scratch in a blank file. Not from memory of the code —
   from memory of the *problem it solves*.
3. Run your version, then diff your thinking against the folder's. The places you
   differ are the lesson.
4. Do one practice problem from the README's list, on a clock.

Step 2 is the one people skip and it's the only one that counts.

## Running the code

Each folder is a flat set of plain `.java` files with no package declaration and
exactly one class with a `main`, called `Demo`.

```
./run.sh <folder>     # compiles that folder and runs Demo
./run.sh              # lists every folder with runnable code
```

If you'd rather not use the script:
`javac -d /tmp/out lld/02-strategy/*.java && java -cp /tmp/out Demo`.

Output is deterministic wherever the topic allows it — clocks are injected rather
than read from the wall, and anything random is seeded. The few genuinely
concurrent demos say so and report aggregate counts rather than pretending a race
is reproducible.

## Licence

Code is MIT, written material is CC BY 4.0. See [LICENSE](LICENSE) — the split is
there so you can lift the Java into your own projects without ceremony, and share
or adapt the notes with attribution.

## Credit

**The HLD track follows Hello Interview's taxonomy.** Folders
[01](hld/01-scaling-reads/) through [08](hld/08-blobs-geo-search/) are the seven
patterns from
[*System Design in a Hurry*](https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction)
— scaling reads, scaling writes, real-time updates, long-running tasks, dealing
with contention, multi-step processes and large blobs — with two changes:
counting and aggregation is pulled out into a step of its own rather than living
inside the others, and large blobs is widened to cover geo and search, which is
why eight folders carry seven patterns. Step
[09](hld/09-technology-deep-dives/) then works through the four technologies
their deep dives lean on hardest: Redis, Kafka, Flink and DynamoDB. The
decomposition is theirs; the Java, the arguments and the drills are mine.

Links to their pages are marked **(premium)** where the page sits behind their
paywall, so you know before you click. Nothing in this repo needs a paid
account.

The practice problems point at
[awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design),
[AlgoMaster](https://algomaster.io) and
[Refactoring Guru](https://refactoring.guru/design-patterns). Those are the
sources worth reading; this repo is the part you have to write yourself, written
out so you can run it first.
