# DynamoDB, in my own words

> **How to fill this in.** Write every section yourself, from your own head,
> without a documentation tab open. Five lines maximum per section — if it takes
> more than five, you are describing rather than explaining, and an interviewer
> will stop you before line six anyway. For each section also name one
> alternative and say when you would pick it. Then finish the file with one
> production story: something that broke, why it broke, and what you changed.
> This file is shorter than the others by design; three sections is the whole
> examinable surface unless you claim more on your CV, in which case add the
> sections you claimed.

---

## Partition key, sort key, and the single-table pattern

*"Model this access pattern in a single table."*


**One alternative:**

**When I'd pick it:**

---

## GSI versus LSI

*"GSI or LSI here, and what does each cost you in consistency?"*


**One alternative:**

**When I'd pick it:**

---

## Hot partitions, adaptive capacity, on-demand versus provisioned

*"One customer is a large share of your traffic. What happens?"*


**One alternative:**

**When I'd pick it:**

---

# Production story

**What broke:**

**Why:**

**How I worked out it was that and not the other plausible causes:**

**What I changed:**

**What it cost:**

*If you have no DynamoDB incident of your own, say so in the interview rather
than borrowing one — "I've used it, I haven't been on call for it" is a
perfectly good answer and it protects the stories that are genuinely yours.
Write down here instead the design decision you made in it and what you would do
differently now.*

*Shape and length for a real story: see the worked example in
[kafka.md](kafka.md).*
