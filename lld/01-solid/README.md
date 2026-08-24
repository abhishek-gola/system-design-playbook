# SOLID as a working tool

Not the definitions. The moment in the code where each one earns its keep.

**The signal:** you catch yourself writing `if (type == ...)`, or editing an
existing class to add a new case.

**What it fixes:** designs that work today and require surgery tomorrow. Every
pattern later in this track is one of these principles made concrete — Strategy
is OCP, Adapter is DIP, Chain of Responsibility is SRP applied to a long method.

---

## One class, refactored five times: a logging framework

Start naive. `Logger.log(String msg, int level)` decides the level with an
if-chain, formats the string inline, and writes to a file. Three separate
reasons to change, all tangled:

```java
class Logger {
    void log(String msg, int level) {
        if (level >= CONFIG_LEVEL) {
            String line = "[" + level + "] " + Instant.now() + " " + msg;
            Files.write(path, line);        // and now add Kafka. And JSON. And async.
        }
    }
}
```

### S — Single responsibility

Split by *reason to change*, not by noun. That distinction is the whole
principle and it's where most explanations go wrong.

| Class | Changes when |
|---|---|
| `LogMessage` | the shape of a log record changes |
| `Formatter` | the output format changes |
| `Sink` | the destination changes |
| `Logger` | the orchestration changes — which is almost never |

Four things, four reasons. "One class one job" is a slogan; "one class one
reason to change" is a test you can actually apply.

### O — Open/closed

Adding a Kafka sink is a new class implementing `Sink`, with zero edits to
`Logger`.

The test to run in your head, out loud, in the interview: *can I name the file
I'd create, and confirm I'd open no existing file?* If the answer involves
editing a switch, you haven't got OCP yet.

### L — Liskov substitution

If `FileSink.write()` never throws on a closed sink, `BufferedFileSink.write()`
mustn't either. Subtypes inherit the *contract*, not just the signature.

The violation always shows up the same way: a caller writing
`if (sink instanceof SomethingSink)`. When you see that, the hierarchy is lying
about what its members can do.

### I — Interface segregation

Only file sinks need `rotate()`. Don't force a console sink to implement it and
throw `UnsupportedOperationException` — that's an LSP violation you created by
getting ISP wrong, which is a nice thing to be able to say out loud.

Split it: `Sink` for everyone, `RotatableSink extends Sink` for the ones that
can. Now the rotation scheduler takes `List<RotatableSink>` and the type system
does the filtering.

### D — Dependency inversion

`Logger` holds `List<Sink>`, never `List<FileSink>`, and takes them through the
constructor.

The payoff is testing. The demo's last section builds a `Logger` over an
`InMemorySink` and asserts on what it captured — no disk, no clock, no cleanup.
If you can't unit-test a class without touching the filesystem, DIP is what
you're missing.

---

## The exercise that actually teaches this

Open a service you've genuinely read at work and find one real violation of each
principle. Write down the fix you'd make. Twenty minutes of that beats a week of
reading definitions, because in the interview you'll be reasoning from a memory
instead of reciting an acronym.

## Also worth having an opinion on

- **DRY** — deduplicate knowledge, not text. Two functions that look identical
  but change for different reasons should stay two functions. Merging them is
  the most common self-inflicted coupling there is.
- **KISS** — the design that is easy to delete beats the design that is easy to
  extend, until you know which direction it will extend in.
- **YAGNI** — an abstraction with one implementation is a guess. Interviewers
  mark down speculative generality as fast as they mark down rigidity.

---

## Run it

```
./run.sh lld/01-solid
```

Writes to a real temp file so `rotate()` does something you can see, then prints
where. The last section is the DIP payoff: the same logger, tested with no I/O
at all.

## Practice

| Problem | What to watch for |
|---|---|
| [Logging Framework](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/logging-framework.md) **(core)** | Do the refactor above for real, in a blank file, from scratch. |
| [Task Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/task-management-system.md) | Watch for the god-class pull: everything wants to live on `Task`. |
| [Coffee Vending Machine](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/coffee-vending-machine.md) | Small surface, and every new drink type tests whether you got OCP right. |

## Read

- [SOLID with code](https://blog.algomaster.io/p/solid-principles-explained-with-code)
- [SOLID in pictures](https://medium.com/backticks-tildes/the-s-o-l-i-d-principles-in-pictures-b34ce2f1e898)
- [DRY / KISS / YAGNI](https://algomaster.io/learn/lld/dry)
