# Observer

**The signal:** "when X happens, also do Y and Z" — and you can tell the list of
Ys is going to grow.

**What it fixes:** a publisher that has to know every consumer by name. Adding a
consumer shouldn't touch the publisher.

---

## Pub-sub, in process

```java
interface Subscriber { void onEvent(Event e); }

class Topic {
    private final List<Subscriber> subs = new CopyOnWriteArrayList<>();
    void subscribe(Subscriber s)   { subs.add(s); }
    void unsubscribe(Subscriber s) { subs.remove(s); }
    void publish(Event e)          { for (Subscriber s : subs) s.onEvent(e); }
}
```

`CopyOnWriteArrayList` rather than `ArrayList` is a small choice that gets
noticed — it means a subscriber can unsubscribe from inside its own callback
without a `ConcurrentModificationException`. Reads are lock-free and writes copy
the array, which is exactly the right trade when subscriptions are rare and
publishes are frequent.

## The follow-up, every time

*"What if one subscriber is slow, or throws?"*

With the loop above, one bad subscriber blocks or breaks all the others. Three
things fix it, and you should name all three:

- **Catch per subscriber**, so one failure doesn't cancel the rest. Cheapest fix,
  do it even in the synchronous version.
- **Dispatch on an executor**, so `publish` returns immediately and a slow
  handler doesn't hold up the publisher.
- **A bounded queue per subscriber**, with an explicit policy when it fills —
  drop, block, or dead-letter. Say which and why.

That last one is the whole answer. An unbounded queue isn't backpressure, it's
an OutOfMemoryError with extra steps.

## The three overflow policies, and when each is right

| Policy | Use when | Cost |
|---|---|---|
| **Drop newest** | metrics, presence pings, anything where the next event supersedes this one | silent data loss, so you must count the drops |
| **Block the publisher** | you'd rather slow the whole system than lose an event | one slow subscriber becomes everyone's problem — the thing you were avoiding |
| **Dead-letter** | you need every event but can't wait | somebody has to drain the dead-letter queue, so it's only a real answer if you say who |

Choosing "block" for an audit-log subscriber and "drop" for a metrics subscriber
*in the same system* is the answer that reads as production experience. The
policy is per subscriber, not global.

## Where this becomes a systems conversation

This is the moment the LLD round quietly turns into an HLD one, and it's your
ground. Name the parallel out loud: **Observer is the in-process version of a
message queue**. Everything you'd say about consumer lag and backpressure in
Kafka applies here in miniature —

- the per-subscriber queue is the consumer's partition backlog
- overflow policy is your retention policy
- a subscriber that keeps throwing is a poison message, and it needs a retry
  limit and a dead-letter path just the same
- and "how do I know a subscriber is falling behind" has the same answer: expose
  the queue depth as a metric and alert on it

See [hld/03-realtime-updates](../../hld/03-realtime-updates/) and
[hld/04-long-running-tasks](../../hld/04-long-running-tasks/) for the same ideas
at scale.

## Push vs pull

The version here pushes the whole event to the subscriber. The alternative is
pushing a notification and letting the subscriber pull what it needs.

Push is simpler and is what you want when the event is small and self-contained.
Pull is what you want when subscribers need different slices of a large object,
or when the event might be stale by the time a slow subscriber gets to it — a
"row 42 changed" notification that makes the subscriber re-read row 42 can never
deliver stale data, which a pushed snapshot can.

---

## Run it

```
./run.sh lld/05-observer
```

Four sections: the plain synchronous topic, a subscriber that throws, a
subscriber that unsubscribes from inside its own callback, and the async version
where a deliberately slow subscriber overflows its queue under all three
policies.

## Practice

| Problem | What to watch for |
|---|---|
| [Pub-Sub System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/pub-sub-system.md) **(core)** | The pattern in its purest form. Then add async dispatch and per-subscriber backpressure. |
| [Online Auction System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/online-auction-system.md) **(core)** | Every bid notifies every watcher. Also a good State problem — do it twice, once through each lens. |
| [Stack Overflow](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/stack-overflow.md) | Notifications on answers, comments and votes, with users subscribing to questions and tags. |

## Read

- [Refactoring Guru — Observer](https://refactoring.guru/design-patterns/observer)
- [AlgoMaster — Observer](https://algomaster.io/learn/lld/observer)
