# Redis, in my own words

> **How to fill this in.** Write every section yourself, from your own head,
> without a documentation tab open. Five lines maximum per section — if it takes
> more than five, you are describing rather than explaining, and an interviewer
> will stop you before line six anyway. For each section also name one
> alternative and say when you would pick it. Then finish the file with one
> production story: something that broke, why it broke, and what you changed.
> This is the cheapest file on the shelf: most of what you already know about
> Redis is recipe knowledge, and writing it out converts it in an afternoon.

---

## The single-threaded event loop

*"Redis is single-threaded. Why is it still fast, and when does that hurt you?"*


**One alternative:**

**When I'd pick it:**

---

## The data structures that are not GET and SET

*"Which structure would you use for this, and why not a sorted set?"*


**One alternative:**

**When I'd pick it:**

---

## RDB versus AOF

*"The Redis box hard-reboots. How much do you lose?"*


**One alternative:**

**When I'd pick it:**

---

## Cluster hash slots and resharding

*"Why can't I run `MULTI` across these two keys?"*


**One alternative:**

**When I'd pick it:**

---

## Lua scripts, atomicity, and the honest argument about Redlock

*"How do you make a read-modify-write atomic? And would you use Redlock for a
distributed lock?"*


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
