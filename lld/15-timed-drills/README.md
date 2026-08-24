# Machine coding at tempo

Not new material. The same problems, on a clock, from a blank file, no notes.

This is the only step on the track that actually builds interview performance.
Everything above it is preparation for it.

**The signal:** you've read the pattern and it made sense. That is not the same
as being able to produce it under a clock with someone watching.

**What it fixes:** the gap between recognising a design and generating one. It
only closes through reps.

There's no code in this folder. There's a method, a rotation, and two files you
fill in yourself.

---

## How to run one

Pick a problem you've already studied. Set a 50-minute timer. Blank file, no
notes, no AI, no reference solution open in another tab.

Talk out loud as if someone's there. Record yourself if you can stand it —
pacing problems are obvious on playback and invisible from the inside.

When the timer stops, **stop**. Then spend ten minutes filling in a row of
[log.md](log.md): which phase overran, what you couldn't remember, what you'd do
differently. That note is worth more than the code.

## The rule about AI

Closed during the block, open afterwards. Use it to review what you wrote and to
explain what you got stuck on — never to produce the design.

The thing you're currently outsourcing is exactly the thing being tested. This is
the rule that's easiest to break and most expensive to break, because breaking it
feels productive.

## The rotation

Five problems cover most of the space. Do each **twice, a fortnight apart**,
rather than ten problems once — the second run is where the fluency comes from,
and the first run of a new problem mostly measures how fast you read.

| Problem | What it drills | Link |
|---|---|---|
| Parking Lot | modelling under time pressure | [problem](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/parking-lot.md) · [folder](../00-modelling/) |
| Rate Limiter | strategy, with a small enough surface to finish | [algorithms](https://blog.algomaster.io/p/rate-limiting-algorithms-explained-with-code) · [folder](../02-strategy/) |
| BookMyShow | concurrency, the hardest common one | [problem](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/movie-ticket-booking-system.md) · [folder](../10-concurrency/) |
| Splitwise | strategy plus non-trivial domain logic | [problem](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/splitwise.md) · [folder](../02-strategy/) |
| Elevator | state plus scheduling | [problem](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/elevator-system.md) · [folder](../06-state/) |

The three marked core on the sheet are Rate Limiter, BookMyShow and Splitwise.
If you only get three timed runs done in a week, do those.

## What to check afterwards

Use [scorecard.md](scorecard.md). Score yourself honestly against the same rubric
every time, because the point is the trend rather than the number.

The five things that actually get graded:

1. **Did you finish?** A complete, plainer design beats an elegant half. If you
   ran out of time, that's the finding, and the fix is pacing rather than
   knowledge.
2. **Did the clock hold?** Coding started before minute 15 and designing stopped
   by minute 25.
3. **Does the extension question have a one-sentence answer?** "How would you
   add X" is what they're actually scoring. If your answer involves editing three
   classes, the pattern was wrong.
4. **Did you say the concurrency story unprompted?** For anything with booking,
   inventory or a balance, silence here fails the round.
5. **Did you narrate?** Playback is brutal about this and it's the fastest thing
   to improve.

## The failure modes, in the order they show up

**Run one:** you spend twenty-five minutes on requirements and entities, panic
at minute thirty, and ship two classes and a stub. Almost everybody does this.
The fix is the timer, not more study.

**Run two:** you pace it correctly but reach for a pattern too early and spend
ten minutes building an abstraction the problem didn't ask for. The fix is to
model with no patterns first, as in [00-modelling](../00-modelling/), and add
them on a second pass.

**Run three:** it's fine, and you discover the extension question is where the
marks are. That's when the rotation starts paying.

If you're still on run one's failure mode after four attempts, the problem is
that you're not actually starting the timer. Start the timer.

## Related

- [00-modelling](../00-modelling/) has the full fifty-minute clock with what
  belongs in each phase.
- Every pattern folder has a Practice table; those problems are the same pool,
  and doing one untimed first then timed a week later is a good rhythm.
