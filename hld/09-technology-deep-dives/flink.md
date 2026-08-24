# Flink, in my own words

> **How to fill this in.** Write every section yourself, from your own head,
> without a documentation tab open. Five lines maximum per section — if it takes
> more than five, you are describing rather than explaining, and an interviewer
> will stop you before line six anyway. For each section also name one
> alternative and say when you would pick it. Then finish the file with one
> production story: something that broke, why it broke, and what you changed.
> Flink is the one where documentation recall is easiest to spot, so be strict
> with yourself here: if a line could have come off a docs page, rewrite it as
> something you learned.

---

## Event time versus processing time

*"Why does Flink make you choose a time semantic at all? Which one do you use,
and why?"*


**One alternative:**

**When I'd pick it:**

---

## Watermarks, allowed lateness, side outputs

*"An event turns up an hour late. What happens to it?"*


**One alternative:**

**When I'd pick it:**

---

## State backends and state that outgrows memory

*"What changes when your keyed state stops fitting in memory?"*


**One alternative:**

**When I'd pick it:**

---

## Checkpointing, barriers, two-phase commit

*"How does a checkpoint work, and how does that become end-to-end
exactly-once?"*


**One alternative:**

**When I'd pick it:**

---

## Backpressure

*"The job is slow. How do you tell a slow sink from a hot key?"*


**One alternative:**

**When I'd pick it:**

---

# Production story

**What broke:**

**Why:**

**How I worked out it was that and not the other plausible causes:**

**What I changed:**

**What it cost:**

*Shape and length: see the worked example in [kafka.md](kafka.md). Sixty seconds
spoken. Symptom, diagnosis, fix, price paid, and the alert you added so it is
caught sooner next time. Every number blank until you have pulled it from a
dashboard or a postmortem.*
