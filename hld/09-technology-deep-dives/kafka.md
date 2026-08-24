# Kafka, in my own words

> **How to fill this in.** Write every section yourself, from your own head,
> without a documentation tab open. Five lines maximum per section — if it takes
> more than five, you are describing rather than explaining, and an interviewer
> will stop you before line six anyway. For each section also name one
> alternative and say when you would pick it. Then finish the file with one
> production story: something that broke, why it broke, and what you changed.
> Come back a week later and read it cold. The lines that make you wince are the
> ones you did not really know.

---

## Partitions, ordering, and key choice

*"If I publish two events for the same order ID, am I guaranteed to read them in
that order?"*


**One alternative:**

**When I'd pick it:**

---

## Consumer groups and rebalancing

*"One of your consumers dies mid-batch. Walk me through what happens."*


**One alternative:**

**When I'd pick it:**

---

## At-least-once, exactly-once, idempotent producers, transactions

*"You've said the pipeline is exactly-once. Exactly-once between which two
points?"*


**One alternative:**

**When I'd pick it:**

---

## Retention versus compaction

*"When would you use a compacted topic rather than just setting a long
retention?"*


**One alternative:**

**When I'd pick it:**

---

## ISR, acks and min.insync.replicas

*"What exact configuration stops you losing an acknowledged write, and what does
it cost you?"*


**One alternative:**

**When I'd pick it:**

---

## Consumer lag

*"Your lag alarm is firing. Take me through the first ten minutes."*


**One alternative:**

**When I'd pick it:**

---

# Production story

## EXAMPLE — delete this and write your own

*This is here so you can see the shape and length expected. It is written from
the example on the sheet, not from your incident notes, and every number in it
is a blank you have to fill from your own dashboards and postmortem. Replace it
entirely.*

During a promotion, consumer lag on the transaction topic climbed to
[__ minutes — get the figure from the lag dashboard for that day] while
throughput on the consumer group looked normal. The graph that mattered was lag
per partition rather than lag for the group: [__ of __] partitions were flat and
one was climbing on its own.

The cause was key choice. We were keying by merchant ID so that all events for
one merchant stayed ordered, and the promotion pushed a single very popular
merchant to [__ % of total events — get this from the event breakdown], so one
partition took traffic several times heavier than any other. Adding consumers
did nothing, because a partition is only ever read by one consumer in the group,
and that consumer was already saturated.

What we changed: [__ what you actually did — composite key, sub-partitioning the
hot key, moving the ordering guarantee somewhere else, or accepting the skew and
sizing for it]. What it cost us: [__ the guarantee or the complexity you traded
away]. What we added so it would be caught sooner: [__ the alert or dashboard,
e.g. per-partition lag rather than group lag].

The lesson worth stating out loud at the end: a key that gives you the ordering
guarantee you want also gives you the traffic distribution you did not choose,
and in most consumer systems the distribution of events across entities is
never close to uniform.

## My story

**What broke:**

**Why:**

**How I worked out it was that and not the other plausible causes:**

**What I changed:**

**What it cost:**
