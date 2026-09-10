# SOLID as a working tool

**The signal:** you catch yourself writing `if (type == ...)`, or editing an
existing class to add a new case.

**What it fixes:** designs that work today and require surgery tomorrow.

---

## How to work this folder

1. Read **What SOLID actually is** below, then the refactor. Fifteen minutes.
2. Run `./run.sh lld/01-solid` and watch the four sections print.
3. Open the code in this order: `Sink.java`, `RotatableSink.java`,
   `Formatter.java`, `Logger.java`, then `Demo.java`. The first four are the
   design; `Demo` is just the driver. Every file's header comment names the
   principle it exists to demonstrate.
4. Close all of it and write the refactor again from a blank file. That step is
   the one that teaches.

---

## What SOLID actually is

Five rules for deciding **where to put a boundary** in object-oriented code.
They are not style rules and they are not about making code pretty. Each one
answers a version of the same question: when this code has to change next year,
how much of it will I have to open?

| | Name | In one sentence |
|---|---|---|
| **S** | Single responsibility | A class should have one reason to change. |
| **O** | Open/closed | You should be able to add behaviour by writing a new class, not by editing an old one. |
| **L** | Liskov substitution | Any subtype must work anywhere its parent type works, without the caller knowing the difference. |
| **I** | Interface segregation | No class should be forced to implement a method that makes no sense for it. |
| **D** | Dependency inversion | Depend on interfaces you define, not on concrete classes someone else defined. |

They are also the foundation of everything later in this track. Strategy is OCP
made concrete, Adapter is DIP, Chain of Responsibility is SRP applied to a
method that grew too long. Learning them here means the patterns later are
recognition rather than memorisation.

---

## The problem: one class doing three jobs

A logging framework is the standard interview vehicle for this because the
naive version is genuinely reasonable, and it still goes wrong.

```java
class Logger {
    void log(String msg, int level) {
        if (level >= CONFIG_LEVEL) {
            String line = "[" + level + "] " + Instant.now() + " " + msg;
            Files.write(path, line);
        }
    }
}
```

Nothing here is stupid. It works. The problem is what happens when three
different people arrive with three different requests:

- *"Log to Kafka as well as the file."* You edit `Logger`.
- *"Ops needs JSON, not that bracket format."* You edit `Logger`.
- *"Add a request ID to every line."* You edit `Logger`.

Three unrelated reasons, one file. Every change risks breaking the other two,
and the class can never be tested without touching a disk. That is the whole
disease, and the five principles are five separate cuts through it.

---

## S: split by reason to change

> **The principle:** a class should have one reason to change.

The trick is the word *reason*. Most explanations say "one class, one job",
which sounds right and helps nobody, because "logging" is one job and we just
saw it contains three.

Ask instead: **who would file the ticket?** A different requester means a
different reason means a different class.

| Class | Changes when | Requested by |
|---|---|---|
| `LogMessage` | the shape of a log record changes | whoever owns the log schema |
| `Formatter` | the output format changes | ops, or whoever parses the logs |
| `Sink` | the destination changes | infrastructure |
| `Logger` | the orchestration changes, which is almost never | nobody |

Four reasons, four types. That is the entire first cut:

```java
final class LogMessage { Instant at; LogLevel level; String logger;
                         String text; Map<String,String> context; }

interface Formatter { String format(LogMessage message); }

interface Sink { void write(String line); void close(); }
```

`LogMessage` is immutable, because a log record you can edit after the fact is a
log record you cannot trust.

## O: add by writing, not by editing

> **The principle:** open for extension, closed for modification. New behaviour
> should mean a new file, not a change to an existing one.

`Logger` now holds `List<Sink>`. Adding Kafka is:

```java
class KafkaSink implements Sink {
    public void write(String line) { producer.send(line); }
    public void close() { producer.flush(); }
}
```

and nothing else. `Logger` is not opened. Neither is `FileSink`, `ConsoleSink`,
or any formatter.

**The test to run in your head, out loud, in the interview:** *can I name the
file I would create, and confirm I would open no existing file?* If your answer
involves editing a switch, you have not got OCP yet. That sentence is worth
memorising, because it converts a vague principle into a yes-or-no check.

## L: subtypes must not lie

> **The principle:** if code works with a `Sink`, it must keep working when you
> hand it any particular kind of `Sink`, without being told which one it got.

Concretely: if `FileSink.write()` never throws on a closed sink, then
`BufferedFileSink.write()` must not throw either. A subtype inherits the
*contract*, not just the method signature.

**The violation always announces itself the same way:**

```java
if (sink instanceof FileSink) { ... }   // <- the hierarchy is lying
```

The moment a caller has to ask which subtype it is holding, the parent type has
promised something its children do not all deliver. When you see `instanceof` in
a caller, look for the broken promise rather than deleting the `instanceof`.

## I: do not force methods onto classes that cannot honour them

> **The principle:** keep interfaces narrow enough that every implementer can
> honestly implement all of it.

Only file sinks can rotate. A console sink cannot. The tempting move is to put
`rotate()` on `Sink` and have `ConsoleSink` throw:

```java
class ConsoleSink implements Sink {
    public void rotate() { throw new UnsupportedOperationException(); }  // wrong
}
```

That is an ISP violation, and notice what it just created: a subtype that
breaks when used through its parent type, which is an **LSP violation you caused
by getting ISP wrong.** Being able to say that sentence out loud is worth a mark
on its own, because it shows you see the principles as connected rather than as
a list.

The fix is to split the interface:

```java
interface Sink { void write(String line); void close(); }

interface RotatableSink extends Sink { void rotate(); }
```

Now the rotation scheduler takes `List<RotatableSink>` and **the type system
does the filtering**. No `instanceof`, no exception, no runtime check.

Note that `close()` stays on `Sink`. `ConsoleSink.close()` is genuinely empty
because there is genuinely no resource to release, which is different from a
method that cannot be honoured. That distinction is the line between ISP and
over-splitting.

## D: depend on the interface, receive it from outside

> **The principle:** depend on abstractions you own, and let someone else decide
> which concrete thing you get.

```java
public Logger(String name, LogLevel threshold, Formatter formatter, List<Sink> sinks) {
    ...
}
```

`Logger` holds `List<Sink>`, never `List<FileSink>`, and receives them through
the constructor. It has never heard of a file.

**The payoff is testing**, and this is the part to say out loud. The demo's last
section builds a `Logger` over an `InMemorySink` and asserts on exactly what it
captured. No disk, no temp directory, no clock, no cleanup:

```java
InMemorySink captured = new InMemorySink();
Logger logger = new Logger("test", LogLevel.INFO, new PlainFormatter(), List.of(captured));
logger.error("boom");
// assert on captured.lines()
```

If you cannot unit-test a class without touching the filesystem, DIP is what you
are missing. That is the most useful one-line diagnostic in the whole acronym.

---

## The finished design

```
LogMessage      immutable record of one log event
Formatter       LogMessage -> String          (PlainFormatter, JsonFormatter)
Sink            String -> somewhere           (ConsoleSink, InMemorySink)
RotatableSink   Sink + rotate()               (FileSink)
Logger          holds a Formatter and a List<Sink>, and orchestrates
```

Count the `if`s in `Logger`: one, and it is a level threshold, not a type check.
That count is the measurable result of the whole refactor.

| Principle | What it bought |
|---|---|
| S | Four small types with four owners, instead of one file three teams fight over |
| O | A new sink or format is a new file; `Logger` is never reopened |
| L | No caller ever asks what kind of `Sink` it is holding |
| I | `ConsoleSink` never has to pretend it can rotate |
| D | The logger is unit-testable with no I/O at all |

---

## Also worth having an opinion on

- **DRY.** Deduplicate knowledge, not text. Two functions that look identical
  but change for different reasons should stay two functions. Merging them is
  the most common self-inflicted coupling there is.
- **KISS.** The design that is easy to delete beats the design that is easy to
  extend, until you know which direction it will extend in.
- **YAGNI.** An abstraction with one implementation is a guess. Interviewers
  mark down speculative generality as fast as they mark down rigidity.

## The exercise that actually teaches this

Open a service you have genuinely read at work and find one real violation of
each principle. Write down the fix you would make. Twenty minutes of that beats
a week of reading definitions, because in the interview you will be reasoning
from a memory instead of reciting an acronym.

## Run it

```
./run.sh lld/01-solid
```

Four sections: three destinations with `Logger` knowing about none of them,
rotation available only where the type allows it, a new format with `Logger`
untouched, and the same logger tested with no I/O at all. It writes to a real
temp file so `rotate()` does something you can see, then prints where.

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
