# Estimation cheatsheet

The numbers below are the ones worth holding in your head, because each of them
can change a design decision inside the five minutes you're allowed for maths.
Anything that can't change a decision has been left out on purpose.

Two rules before the tables. Round everything to one significant figure and to
powers of ten — you are looking for the order of magnitude, and precision you
can't justify wastes the clock. And say the conclusion, not the arithmetic:
"that's about 30,000 reads a second at peak, so the primary alone won't do it"
is the sentence that earns the time you just spent.

## Powers of two, and the only ones you need

| Power | Exact | Call it | Reads as |
|---|---|---|---|
| 2^10 | 1,024 | 1 thousand | KB |
| 2^20 | 1,048,576 | 1 million | MB |
| 2^30 | ~1.07 billion | 1 billion | GB |
| 2^40 | ~1.1 trillion | 1 trillion | TB |
| 2^50 | ~1.13 quadrillion | 1 quadrillion | PB |

Two more that come up in ID design: 2^32 is about 4.3 billion, which is why a
32-bit user ID is not enough for a global consumer product, and 2^63 is about
9.2 × 10^18, which is why a signed 64-bit ID is enough for anything you will
ever build. A base62 short code of length 7 gives 62^7, about 3.5 × 10^12 —
plenty for a URL shortener, and worth knowing because it is the one calculation
Bitly always asks for.

## Time, rounded for mental arithmetic

| Interval | Exact seconds | Use |
|---|---|---|
| One day | 86,400 | **100,000** — this single substitution is most of back-of-envelope maths |
| One month | ~2,600,000 | 2.5 million |
| One year | ~31,500,000 | 30 million |

Rounding 86,400 up to 100,000 makes your QPS about 15% low. Nobody cares, and
saying "call it 100k seconds in a day" out loud shows you know the trick rather
than that you got it wrong.

## Latency numbers every programmer should know

The classic table. Treat these as shape rather than as measurements — the
hardware has moved on since they were written, particularly for SSDs and network
round trips, but the ratios between the lines are what you actually reason with.

| Operation | Time | In human terms |
|---|---|---|
| L1 cache reference | 0.5 ns | |
| Branch mispredict | 5 ns | |
| L2 cache reference | 7 ns | 14× L1 |
| Mutex lock/unlock | 25 ns | |
| Main memory reference | 100 ns | 200× L1 |
| Compress 1 KB | ~3 µs | |
| Send 1 KB over 1 Gbps network | ~10 µs | |
| Random read from SSD | ~150 µs | |
| Read 1 MB sequentially from memory | ~250 µs | |
| Round trip inside one datacentre | ~500 µs | |
| Read 1 MB sequentially from SSD | ~1 ms | 4× memory |
| Disk seek | ~10 ms | |
| Read 1 MB sequentially from spinning disk | ~20 ms | 80× memory |
| Round trip across the Atlantic and back | ~150 ms | |

The three conclusions you should be able to draw from this table on demand:

- **Memory is roughly a hundred times faster than SSD, and a thousand times
  faster than a disk seek.** That is the entire argument for a cache, and it is
  why "add Redis" is a real answer rather than a reflex.
- **A cross-region round trip costs more than everything else in your request
  combined.** If your p99 budget is 200 ms and one call crosses an ocean, the
  design is already finished — you either move the data or accept the latency,
  and no amount of tuning inside the datacentre matters.
- **Sequential beats random by a lot on every storage medium.** That is why
  LSM trees exist, why batching writes wins, and why appending to a log is the
  cheapest durable thing you can do. It is the same insight behind
  [02-scaling-writes](../02-scaling-writes/).

## Bytes per row

Build a row estimate from parts rather than guessing at the total. The parts are
exact; the total is yours to defend.

| Type | Bytes |
|---|---|
| boolean | 1 |
| int (32-bit) | 4 |
| bigint, double, timestamp (64-bit) | 8 |
| UUID stored as binary | 16 |
| UUID stored as text | 36 |
| ASCII character | 1 |
| UTF-8 character | 1 to 4 (emoji are 4) |
| IPv4 address as text | up to 15 |
| A URL | ~100 on average, cap at 2,048 |
| A short text post | ~300 for 280 characters, more if it's not ASCII |
| A thumbnail image | ~10 KB |
| A phone photo | ~2 MB |
| A minute of 1080p video | ~50 MB |

Then add overhead. A rule of thumb that has never let me down: take the sum of
the fields, add 20% for row headers and padding, and add roughly the size of the
indexed columns again for every secondary index. A 100-byte row with two
secondary indexes is closer to 200 bytes on disk, and if your estimate is within
2× of the truth it has done its job.

## DAU to QPS

```
average QPS = DAU × actions per user per day ÷ 100,000
peak QPS    = average QPS × 2 to 3
```

The peak multiplier is a modelling assumption, so say it out loud rather than
smuggling it in: "I'll assume peak is three times average, which is typical for
a consumer app with one time zone dominating." If the product is genuinely
spiky — ticket sales, live sport, a flash sale — the multiplier is not 3, it is
20 or 100, and that changes the design from "size for peak" to "queue and shed",
which is the whole point of asking.

Two shortcuts worth memorising because they come up constantly:

- **1 million DAU with 10 actions each is about 100 QPS average.** Anchor
  everything to this one and scale it.
- **1 billion actions a day is about 10,000 QPS average.**

## QPS to storage

```
bytes per day  = write QPS × bytes per row × 100,000
bytes per year = bytes per day × 365
```

Anchors:

- **1,000 writes/sec of 1 KB rows is about 100 GB a day**, which is 36 TB a
  year. That is the point where retention tiers stop being optional.
- **1 GB a day is about 365 GB a year.** Round to a third of a terabyte.
- Multiply by the replication factor at the end, not in the middle. Three copies
  of 36 TB is 108 TB, and forgetting the factor of three is the most common
  arithmetic slip in this part of the interview.

## Bandwidth

```
bytes per second = QPS × response size
```

10,000 QPS of 10 KB responses is 100 MB/s, which is about 800 Mbps and therefore
a real cost line and a real reason to put media on a CDN. This is usually the
calculation that justifies the edge, so do it whenever the payload is images or
video rather than JSON.

## A worked example: the URL shortener

This is the anchor problem for [01-scaling-reads](../01-scaling-reads/), and it
is a good example precisely because most of the numbers turn out not to matter.
Assume 100 million DAU, each following 10 links a day, and 1 million new links
created a day.

**Reads.** 100M × 10 = 1 billion redirects a day. Divide by 100,000 seconds and
you get 10,000 QPS average, so call it 30,000 at peak.

**Writes.** 1 million a day ÷ 100,000 = 10 writes a second. Peak, say 30.

**The ratio.** A thousand reads for every write. That single number decides the
architecture: this is a read-scaling problem, the write path can stay boring,
and anyone who spends the deep dive on write throughput has misread the prompt.

**Storage.** A row is a 7-byte code, a URL at about 100 bytes, an 8-byte owner
ID, and two 8-byte timestamps — roughly 130 bytes, so call it 200 with overhead
and the index on the short code. At 1 million rows a day that's 200 MB a day, 73
GB a year, under 400 GB over five years.

**The conclusion, which is the part that matters.** Five years of data fits on a
single disk, so sharding for capacity is not the problem and you should say so
rather than sharding out of habit. The hot set is smaller still: if the top
million links carry most of the traffic, that's 200 MB of cache, which fits in
memory on one node with room to spare. So the design is an index, a cache with
a long TTL because the mapping is immutable, read replicas behind it, and the
edge for the redirect itself. Every one of those follows from the arithmetic
above, and the whole calculation takes three minutes.

**What would change the answer.** If the URLs averaged 2 KB instead of 100
bytes, storage becomes 4 TB over five years and the cache stops being free. If
creations were 100 million a day rather than 1 million, you'd be in
write-scaling territory and choosing a shard key. Knowing which input flips the
design is worth more than the output itself, and offering that unprompted is a
senior signal.

## When to skip the maths entirely

If the interviewer has already given you the scale, don't re-derive it. If the
system is internal and bounded — a company's own metrics, a school's timetable,
a warehouse's inventory — say "this is small enough that a single Postgres
instance handles it, so I'll spend the time on the concurrency instead" and move
on. Choosing not to estimate, with a reason, reads as judgement. Estimating
because the script said to reads as ritual.
