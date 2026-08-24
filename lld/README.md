# Low-level design

Sixteen folders in study order. The sheet groups them into weekly steps; the
mapping is below, because the grouping matters — the four in step 02 are the
ones that actually turn up in Indian LLD rounds, and everything in step 05 is
there because interviewers ask, not because you'll use it.

| Step | Week | Folders |
|---|---|---|
| 00 · Modelling, before any pattern | 1 | [00-modelling](00-modelling/) |
| 01 · SOLID as a working tool | 1 | [01-solid](01-solid/) |
| 02 · The four that pay the rent | 2 | [02-strategy](02-strategy/), [03-factory](03-factory/), [04-builder](04-builder/), [05-observer](05-observer/) |
| 03 · The next four | 3 | [06-state](06-state/), [07-chain-of-responsibility](07-chain-of-responsibility/), [08-decorator](08-decorator/), [09-adapter](09-adapter/) |
| 04 · Concurrency inside the design | 4 | [10-concurrency](10-concurrency/) |
| 05 · Worth knowing, not worth drilling | 4 | [11-singleton](11-singleton/), [12-composite](12-composite/), [13-command](13-command/), [14-remaining-patterns](14-remaining-patterns/) |
| 06 · Machine coding at tempo | 5–14 | [15-timed-drills](15-timed-drills/) |

## Core path

If the week goes badly and you only get through some of it, get through these.
They're the subset that gets you to a passing design round:

`00-modelling` · `01-solid` · `02-strategy` · `03-factory` · `05-observer` ·
`06-state` · `07-chain-of-responsibility` · `10-concurrency` · `15-timed-drills`

Builder, decorator, adapter, singleton, composite and command are depth. Worth
knowing, not worth a whole session until the core is automatic.

## The two rules that decide the round

**Never start coding before minute 15. Never still be designing at minute 25.**
Pacing loses more LLD rounds than knowledge does. [00-modelling](00-modelling/)
has the full clock; set a timer on every practice run until it's automatic.

**Narrate.** Silence reads as being stuck even when you're thinking clearly. Say
what you're weighing out loud, including the things you decide against.

## Picking the pattern from the requirement

This is the table to internalise. In the interview you get a sentence of
English, and the job is to hear which pattern it's asking for.

| What they say | What it means |
|---|---|
| "should support multiple ways to ___" | Strategy |
| "the object goes through these stages" | State |
| "when X happens, also do Y and Z" | Observer |
| "the request passes these checks, any can reject" | Chain of responsibility |
| "features can be combined in any order" | Decorator |
| "we integrate with three providers" | Adapter |
| "lots of optional fields, must be valid once built" | Builder |
| "create the right kind of X based on Y" | Factory |
| "a group behaves like a single one" | Composite |
| "undo, replay, schedule for later" | Command |
| "two users do this at the same time" | Not a pattern — see [10-concurrency](10-concurrency/) |

The last row is the one to watch for. Anything with booking, seats, inventory,
balance or a counter is a concurrency question wearing a pattern costume, and
answering it with a clean class hierarchy and no locking story fails.
