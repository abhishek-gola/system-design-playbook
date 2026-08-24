# Strategy

**The signal:** the requirement says "should support multiple ways to ___" —
pricing rules, split methods, eviction policies, matching algorithms. Or you're
looking at a `switch` that picks an algorithm.

**What it fixes:** behaviour that varies independently of the thing using it.
Without it, every new algorithm means editing the same class again.

This is the single highest-value pattern on the track. Learn it first, and learn
it on a rate limiter — it is small enough to finish, and the follow-up questions
are the same ones every Strategy problem asks.

---

## A rate limiter with swappable algorithms

```java
interface RateLimitStrategy {
    boolean allow(String key);          // one decision, no side effects the caller cares about
}

class TokenBucketStrategy         implements RateLimitStrategy { ... }
class SlidingWindowLogStrategy    implements RateLimitStrategy { ... }
class FixedWindowCounterStrategy  implements RateLimitStrategy { ... }

class RateLimiter {
    private final Map<String, RateLimitStrategy> byRoute;   // injected
    private final RateLimitStrategy fallback;

    boolean isAllowed(Request r) {
        return byRoute.getOrDefault(r.route(), fallback).allow(r.clientKey());
    }
}
```

The follow-up is always the same: *"now make the limit different per endpoint"*,
or *"now let ops change the algorithm without a deploy"*. You answer by keying
strategies by route and loading the map from config. No `if` anywhere.

That answer is the entire reason they asked this question.

## The three algorithms, and when each is right

| | Memory per key | Burst behaviour | Boundary problem |
|---|---|---|---|
| **Fixed window counter** | one int | allows 2× the limit across a boundary | yes, and it's why nobody ships it alone |
| **Sliding window log** | one timestamp per request | exact, no bursts | none |
| **Token bucket** | two numbers | allows a controlled burst up to capacity | none |

Token bucket is the default answer for an API gateway: cheap, and the burst is
usually a feature rather than a bug — clients retry in clusters and you'd rather
absorb that than reject it.

Sliding window log is what you pick when the limit is a contractual promise (a
payment provider allowing exactly 100 requests a minute) and you can afford to
store a timestamp per request.

Fixed window is what you pick when memory is the binding constraint and you can
live with a client sending 2N requests across the boundary between two windows.
Say that boundary flaw out loud before the interviewer finds it.

## Where it shows up when it isn't called Strategy

A `Comparator` passed to `sort`. A `Predicate` passed to `filter`. Spring
injecting one of three beans by profile. `ThreadPoolExecutor` taking a
`RejectedExecutionHandler`. You have almost certainly been using this pattern
for years without calling it that — the interview just wants you to name it.

## Strategy vs State

Strategy is chosen from outside and never changes itself. State changes itself.

If the object flips its own behaviour as events arrive, you want State (see
[06-state](../06-state/)), and confusing the two in an interview is a common
tell. The structural giveaway: a Strategy has no reference back to its context;
a State usually does, because it needs to set the next one.

## The mistake to avoid

Don't make the strategy interface too wide. `allow(String key)` returning a
boolean is right. `allow(Request r, Config c, Clock clock, Metrics m)` is a
strategy that has become a second copy of the limiter, and every new
implementation has to reimplement all of it.

If you find the interface growing, the shared parts belong in the context class,
not in the strategy.

---

## Run it

```
./run.sh lld/02-strategy
```

All three algorithms, driven by a fake clock so the output is the same every
time. The demo ends by swapping one route's algorithm at runtime — that's the
follow-up question, answered in one line.

Note the `Ticker` interface: time is injected, not read from
`System.currentTimeMillis()` inside the algorithms. That's DIP, and it's the
only reason a rate limiter is testable without sleeping.

## Practice

| Problem | What to watch for |
|---|---|
| [Splitwise](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/splitwise.md) **(core)** | Equal / exact / percentage splits are three strategies behind one interface. Debt simplification is the stretch. |
| [LRU Cache with pluggable eviction](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/lru-cache.md) **(core)** | Build LRU first, then make the policy swappable so LFU and FIFO drop in without touching the cache. |
| [Ride-Sharing Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/ride-sharing-service.md) | Two strategies at once: fare calculation and driver matching. Keep them independent. |

## Read

- [Refactoring Guru — Strategy](https://refactoring.guru/design-patterns/strategy)
- [AlgoMaster — Strategy](https://algomaster.io/learn/lld/strategy)
- [Rate limiting algorithms with code](https://blog.algomaster.io/p/rate-limiting-algorithms-explained-with-code)
