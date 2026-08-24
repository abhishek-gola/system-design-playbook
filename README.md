# System Design Playbook

**A design interview guide you can run.** Low-level and high-level design,
arranged as a practice sheet: every pattern gets a folder with the signal that
tells you to reach for it, one worked example in dependency-free Java, and three
problems to practise on.

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

**You can run all of it.** 27 folders, 200-plus Java files, no Maven, no Gradle,
no dependencies. Every folder compiles and runs clean on OpenJDK 21. A watermark
either drops a late event or routes it to a side output, and you can watch it
happen instead of reading a claim about it.

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

The practice problems point at
[awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design),
[Hello Interview](https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction),
[AlgoMaster](https://algomaster.io) and
[Refactoring Guru](https://refactoring.guru/design-patterns). Those are the
sources worth reading; this repo is the part you have to write yourself, written
out so you can run it first.
