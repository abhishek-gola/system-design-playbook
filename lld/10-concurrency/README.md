# Concurrency inside the design

Not a GoF pattern, and the axis that decides more LLD rounds than any single
pattern does. Anything with booking, inventory, balance or a counter lands here.

**The signal:** the problem mentions booking, seats, inventory, wallet balance
or a counter — or the interviewer asks "what if two users do this at the same
time?"

**What it fixes:** double-booking, lost updates, and the answer "I'd add
`synchronized`" delivered without knowing what it costs.

The system-scale version of this same problem is
[hld/05-contention](../../hld/05-contention/). Read them together; the ladder is
the same and only the tools change.

---

## The race, stated precisely

Two threads read `seat.status == AVAILABLE`. Both pass the check. Both write
`BOOKED`. Two people own seat 14A.

The check and the write have to be **one atomic step**. Everything below is a
different way of buying that atomicity, and the differences are about cost, not
correctness.

## The four options, cheapest first

**Compare-and-set on a concurrent map.** `seatHolds.putIfAbsent(seatId, token)`
— exactly one caller gets `null` back and wins. No lock, no blocking, and it
works beautifully for the single-process version of this question. Start here.

**Optimistic concurrency.** A version column; the update says
`WHERE id = ? AND version = ?` and you check the affected-row count. Cheap when
conflicts are rare, wasteful when they're common, because every loser redoes its
work.

**Pessimistic locking.** `synchronized` on the show, a per-seat `ReentrantLock`,
or `SELECT ... FOR UPDATE`. Correct, and it serialises everything behind that
lock. If you lock several seats for a group booking, **acquire them in a fixed
global order or you'll deadlock** — say that unprompted, because it's the
follow-up.

**Serialise by partition.** Route all operations for one show to one consumer or
one thread. No locks at all, at the cost of a scaling ceiling per show.

## Lock granularity is the interesting decision

`synchronized (this)` on the whole show is correct and turns a 500-seat cinema
into a queue of one. A `ReentrantLock` per seat lets 500 bookings proceed at
once and costs you a map of locks plus the deadlock risk above.

The rule worth stating: **lock the smallest thing that makes the invariant
true.** If the invariant is "this seat has one owner", lock the seat. If it's
"this show never sells more than capacity", you need something that covers the
whole show — and that's a counter, not a lock.

## What actually ships: hold, then confirm

Reserve the seat with a ten-minute TTL, take payment, then confirm.

This is the key insight and it's worth stating explicitly: it converts a lock
held for minutes across a payment call — which you must never do — into a
database row with an expiry. Nothing is held while you wait on a third party.

You then need a sweeper or a delay queue to release abandoned holds, and *that's
the follow-up question*, so have the answer ready. Two ways:

- a background sweeper that scans for expired holds, which is simple and has a
  window where an expired hold still looks live
- lazy expiry at read time — treat any hold past its TTL as absent the moment
  someone asks — which has no window and no background job, and is what the code
  here does

Say both. The lazy version is the one that's actually correct under a crash,
because it doesn't depend on a sweeper being alive.

## The Java details they probe

| Question | The answer they want |
|---|---|
| `synchronized` vs `ReentrantLock` | `ReentrantLock` gives you `tryLock` with a timeout, which is how you avoid hanging forever, plus fairness and multiple conditions. `synchronized` is simpler and JIT-friendlier. Use `synchronized` unless you need one of those. |
| `ConcurrentHashMap` vs `Collections.synchronizedMap` | Lock striping and lock-free reads versus one global lock around every operation. Also: `synchronizedMap` still needs external synchronisation for check-then-act, which is the bug this whole folder is about. |
| What does CAS actually do | A single hardware instruction that compares a memory location to an expected value and swaps only if it matches. It's how `AtomicInteger` works, and why it can fail and retry rather than block. |
| Why doesn't `volatile` make `count++` safe | `volatile` gives visibility, not atomicity. `count++` is read-modify-write — three steps — and two threads can interleave inside it. Use `AtomicInteger`. |
| What's a happens-before edge | Unlocking a monitor happens-before locking it, and a `volatile` write happens-before a subsequent read. It's the guarantee that makes anything one thread wrote visible to another. |

## The mistake that fails the round

Answering "I'd make the method `synchronized`" and stopping. It's not wrong, and
that's what makes it dangerous — the candidate thinks they've answered. What's
missing is any statement about **what it costs** and **what it doesn't cover**.

A synchronized method on a single instance does nothing once you run two
application servers, and every booking system runs two application servers.
Saying that yourself, before they ask, is the difference between a passing
answer and a good one.

---

## Run it

```
./run.sh lld/10-concurrency
```

Five sections: the race reproduced with real threads and counted, compare-and-set,
per-seat locking, a group booking deadlocking and then fixed by ordering, and
hold-then-confirm with a manual clock so expiry is visible without waiting ten
minutes.

The race section prints how many threads believed they'd won the same seat. If
it happens to come out as one on your machine, it says so rather than pretending
— a race that doesn't reproduce on this run is still a bug, and that's a useful
thing to see stated.

## Practice

| Problem | What to watch for |
|---|---|
| [Movie Ticket Booking System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/movie-ticket-booking-system.md) **(core)** | Seat locking is the whole question. Implement hold-then-confirm with expiry, not just a lock. |
| [Thread-Safe Cache with TTL](https://algomaster.io/learn/concurrency-interview/design-thread-safe-cache-with-ttl) **(core)** | Concurrent reads, expiry sweeping, and no stampede when a hot key expires. |
| [Thread-Safe Blocking Queue](https://algomaster.io/learn/concurrency-interview/design-thread-safe-blocking-queue) | `wait`/`notify` or condition variables, done properly. Bounded, with both ends blocking. |

## Read

- [Race conditions and critical sections](https://algomaster.io/learn/concurrency-interview/race-conditions-and-critical-sections)
- [Compare-and-swap](https://algomaster.io/learn/concurrency-interview/compare-and-swap)
- [Coarse vs fine-grained locking](https://algomaster.io/learn/concurrency-interview/coarse-vs-fine-grained-locking)
- [Deadlock](https://algomaster.io/learn/concurrency-interview/deadlock)
