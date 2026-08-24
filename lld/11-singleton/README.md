# Singleton, done properly

**The signal:** exactly one instance must exist — a registry, a connection pool,
a config holder.

**What it fixes:** almost nothing, honestly. It's here because interviewers ask,
and because the thread-safety discussion behind it is real.

---

## Three implementations and what each one costs

```java
// 1. Eager. Simple, correct, no laziness.
class Config { private static final Config I = new Config(); static Config get(){ return I; } }

// 2. Double-checked locking. The volatile is the entire question.
class Config {
    private static volatile Config i;
    static Config get() {
        if (i == null) {
            synchronized (Config.class) { if (i == null) i = new Config(); }
        }
        return i;
    }
}

// 3. Enum. Serialisation and reflection safe. What Effective Java recommends.
enum Config { INSTANCE; }
```

There's a fourth worth knowing — the **holder idiom** — which gets you laziness
with no synchronisation at all by leaning on the class loader:

```java
class Config {
    private static class Holder { static final Config I = new Config(); }
    static Config get() { return Holder.I; }
}
```

`Holder` isn't initialised until `get()` first touches it, and the JVM
guarantees class initialisation is thread-safe. No `volatile`, no lock, no cost
on the fast path. If you're writing a lazy singleton in Java and you don't need
it to be an enum, write this one.

## The volatile question

Without `volatile` in the double-checked version, another thread can see a
non-null reference to a **partially constructed object**, because the assignment
to `i` and the constructor's writes to the object's fields can be reordered.

That sentence is the answer they're fishing for. The longer version, if they
want it: `i = new Config()` is really three steps — allocate, run the
constructor, publish the reference. The JVM is allowed to reorder steps two and
three, so a second thread can see a non-null `i` whose fields are still zero.
`volatile` inserts the happens-before edge that forbids it.

This is also why the pre-Java-5 memory model made double-checked locking simply
broken, and why so much old advice about it is wrong.

## The second half of the question

They'll ask why it's considered an anti-pattern. Answer both sides:

**Against:** global mutable state, hidden dependencies (a class using a
singleton doesn't declare that it does), and code you can't unit-test in
isolation — two tests share the instance and start affecting each other through
it.

**For:** some things genuinely are one per process, and a connection pool that
can be accidentally constructed twice is a worse problem than a slightly awkward
test.

Then say what you'd actually do: **construct one instance and inject it.** The
constraint lives in the wiring rather than in the class. You get one instance,
your tests get their own, and nothing has a hidden dependency. That's what every
dependency injection framework does, and "Spring beans are singletons by default
and none of them are the Singleton pattern" is a good line.

## What this looks like in practice

| Situation | What to write |
|---|---|
| Config, metrics registry, connection pool | one instance, injected through the constructor |
| A genuinely stateless helper | a class with static methods, not a singleton |
| Something you'll swap in tests | an interface plus injection, never a singleton |
| A serialisation-safe constant | `enum` |

## Where singletons bite in real code

Two tests pass individually and fail together. That's the tell, and it's almost
always shared singleton state that one test mutated. The fix is never a
`reset()` method on the singleton, because now you have to remember to call it —
it's injection.

---

## Run it

```
./run.sh lld/11-singleton
```

All four implementations, plus a demonstration that the eager and holder
versions initialise at different moments, and the enum surviving a round trip
through serialisation while a hand-rolled singleton does not.

## Practice

| Problem | What to watch for |
|---|---|
| Thread-safe configuration manager | All four implementations, then explain the `volatile`. [Reference](https://refactoring.guru/design-patterns/singleton/java/example) |
| [Parking Lot](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/parking-lot.md) | The `ParkingLot` itself is the usual singleton in this problem — and a good place to argue for injection instead. See [lld/00-modelling](../00-modelling/), which deliberately doesn't make it one. |
| [Logging Framework](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/logging-framework.md) | The logger registry is a real singleton. The logger itself should not be. |

## Read

- [Refactoring Guru — Singleton](https://refactoring.guru/design-patterns/singleton)
- [AlgoMaster — Singleton](https://algomaster.io/learn/lld/singleton)
