# The rest, in one line each

**The signal:** asked directly, or you spot the shape while designing something
else.

**What it fixes:** nothing you need this quarter. Read the one-liners, run the
demo once, and follow a link only if a problem actually pulls you there.

This folder exists so that "tell me about the Visitor pattern" is a
thirty-second answer rather than a blank. Do not spend a session here until
[02-strategy](../02-strategy/) through [10-concurrency](../10-concurrency/) are
automatic.

---

## The ten

**Template Method** — several flows share a skeleton and differ in two steps.
A base class fixes the order with a `final` method; subclasses fill the gaps.
Prefer Strategy unless the skeleton is genuinely immutable. You have already
seen this: `RiskCheck.handle()` in
[07-chain-of-responsibility](../07-chain-of-responsibility/) is `final` for
exactly this reason.

**Proxy** — stand in front of an object to add lazy loading, caching, access
control or a remote call, without the caller knowing. Structurally identical to
Decorator; the difference is intent, and the answer to "how are they different"
is *controlling access* versus *adding a feature*.

**Facade** — one simple entry point over a messy subsystem. You've written
dozens without naming it. The test that it's a real facade and not just a class:
callers can still reach past it if they need to.

**Iterator** — traverse a collection without exposing its internals. Java's
`Iterable` is this, so an interview question here is always about writing a
custom one, usually over a tree or a paginated API.

**Flyweight** — share immutable intrinsic state across many objects to save
memory. The split that matters is intrinsic (shared, immutable — a chess piece's
colour and kind) versus extrinsic (passed in per use — which square it's on).
Java's `Integer.valueOf` cache is a flyweight you use daily.

**Bridge** — two dimensions varying independently (shape × renderer). Prevents a
class explosion when you'd otherwise multiply the hierarchies. The difference
from Strategy: Bridge is a design decision made up front about the shape of the
whole hierarchy; Strategy is one varying behaviour inside one class.

**Mediator** — objects that would otherwise all reference each other talk
through a hub instead. Air traffic control, chat rooms. It turns an n-squared
mesh into n spokes, and the cost is a hub that slowly becomes a god object,
which is worth naming.

**Memento** — snapshot and restore an object's state without exposing its
internals. The alternative to Command-based undo when the action isn't cleanly
reversible. See [13-command](../13-command/).

**Prototype** — clone an existing object rather than constructing one, when
construction is expensive. In Java the honest note is that `Cloneable` is
broken by design and a copy constructor is what you'd actually write.

**Visitor** — add operations to a stable class hierarchy without editing it.
Rare in interviews, common in compilers. The trade-off to state: it makes new
*operations* cheap and new *node types* expensive, which is the exact opposite
of ordinary polymorphism. Pairs naturally with
[12-composite](../12-composite/).

## If you only remember four things from this page

1. Proxy vs Decorator is *intent*, not structure.
2. Template Method fixes the order, Strategy replaces the behaviour.
3. Flyweight is intrinsic vs extrinsic state, and that's the whole idea.
4. Visitor trades cheap new operations for expensive new node types.

Those four comparisons are what actually gets asked. The rest is recognition.

---

## Run it

```
./run.sh lld/14-remaining-patterns
```

Ten tiny demonstrations, one per pattern, each small enough to read in twenty
seconds. The Flyweight section prints the object count with and without sharing
so the memory argument is a number rather than a claim.

## Practice

| Problem | What to watch for |
|---|---|
| [Social Network like Facebook](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/social-networking-service.md) | Big enough that several of these surface naturally. Notice which ones you reach for. |
| [Online Shopping System like Amazon](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/online-shopping-service.md) | Facade over the checkout subsystem, template method on order processing. |
| [CricInfo](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/cricinfo.md) | Observer, state and composite together. A good end-of-track integration test. |

## Read

- [Full pattern catalogue](https://refactoring.guru/design-patterns/catalog)
- [All patterns in Java](https://refactoring.guru/design-patterns/java)
- [AlgoMaster LLD index](https://github.com/ashishps1/awesome-low-level-design)
