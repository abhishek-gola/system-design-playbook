# Decorator

**The signal:** features stack in combinations, and doing it with inheritance
would need a class per combination — `RetryingLoggingEncryptedSink` and friends.

**What it fixes:** combinatorial explosion of subclasses.

---

## Wrappers that compose in any order

```java
interface Notifier { void send(Message m); }

class RetryingNotifier implements Notifier {
    private final Notifier inner;                 // same interface in, same interface out
    public void send(Message m) {
        for (int i = 0; i < 3; i++) {
            try { inner.send(m); return; } catch (TransientException e) { backoff(i); }
        }
        throw new DeliveryFailed();
    }
}

Notifier n = new RetryingNotifier(
                 new RateLimitedNotifier(
                     new MetricsNotifier(new EmailNotifier())));
```

The defining property is that a decorator **takes the interface it implements**
— same in, same out. That's what lets them nest in any order, and it's the
answer to "how is this different from just wrapping it in a helper class?"

Four features would be sixteen subclasses with inheritance. Here they're four
classes, and the combination is chosen at the call site.

## Order is not arbitrary

Candidates say "they compose in any order" and then stop. The interesting
follow-up is that the *order changes the behaviour*, and knowing which order you
want is the real skill.

| Stack | What happens |
|---|---|
| `retry(rateLimit(metrics(email)))` | every retry passes back through the limiter, so **retries burn quota**. The third message is rejected outright because the first two spent four tokens between them. |
| `rateLimit(retry(metrics(email)))` | the limiter is consulted **once per logical send** and the retries happen underneath it. All three messages get through and there's quota left over. |
| `metrics(retry(rateLimit(email)))` | metrics now counts **one call per logical send** rather than per attempt, so your success rate is measured against user intent instead of network attempts. |

The demo runs all three against the same flaky transport and prints the
counters, so you can see them come out differently. If someone asks you to
design a client wrapper stack, "I'd put the retry inside the rate limiter so
retries don't burn quota" is a very specific, very credible sentence — and the
second row is why.

## Decorator vs Proxy

Identical shape, different intent.

A **decorator** adds behaviour the caller wants and knows about. A **proxy**
controls access to something the caller can't or shouldn't reach directly — lazy
loading, permission checks, a remote call.

If asked, answer with intent, not structure. "Structurally they're the same; the
difference is whether I'm adding a feature or controlling access."

## Decorator vs middleware

They're the same idea, and saying so is worth a mark. A servlet filter chain, an
Express middleware stack, a gRPC interceptor list — all decorator, just built
by a framework from a list instead of by you with constructors.

Which suggests the variant worth showing if they push: build the stack from a
config list rather than nesting constructors by hand.

```java
List<UnaryOperator<Notifier>> layers = List.of(
    MetricsNotifier::new, RateLimitedNotifier::new, RetryingNotifier::new);

Notifier n = base;
for (var layer : layers) n = layer.apply(n);
```

## Where it goes wrong

A decorator that needs to know what it's wrapping isn't a decorator. If
`RetryingNotifier` has to check `if (inner instanceof EmailNotifier)`, the
abstraction is wrong — usually because the interface is too narrow and the
decorator needs information the interface doesn't carry.

Fix the interface, don't add the instanceof.

---

## Run it

```
./run.sh lld/08-decorator
```

Builds the same three layers in three different orders against a flaky
transport, and prints the resulting counters so you can see the ordering matter.

## Practice

| Problem | What to watch for |
|---|---|
| [Coffee Vending Machine](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/coffee-vending-machine.md) | Base drink plus milk, syrup, extra shot. The canonical decorator problem. |
| [Online Food Delivery Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/food-delivery-service.md) | Order-level add-ons, packaging charges and offers stacked on a base order. |
| [Music Streaming Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/music-streaming-service.md) | Wrap the audio stream: equaliser, normalisation, bitrate adaptation. |

## Read

- [Refactoring Guru — Decorator](https://refactoring.guru/design-patterns/decorator)
- [AlgoMaster — Decorator](https://algomaster.io/learn/lld/decorator)
