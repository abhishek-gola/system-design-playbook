# Command

**The signal:** undo, redo, replay, schedule-for-later, queue-this-action, or an
audit log of what was done.

**What it fixes:** actions that exist only as method calls, which means you
can't store, reverse, or defer them.

---

## A chess move that knows how to reverse itself

```java
interface Command { void execute(); void undo(); }

class MoveCommand implements Command {
    private final Square from, to;
    private Piece captured;                  // recorded during execute

    public void execute() { captured = board.pieceAt(to); board.move(from, to); }
    public void undo()    { board.move(to, from); board.place(captured, to); }
}

class Game { private final Deque<Command> history = new ArrayDeque<>(); }
```

The command stores enough state to reverse itself — the captured piece — which
is precisely why undo works.

That's the design rule worth stating: **a command must capture, at execute time,
whatever the world is about to forget.** If it doesn't, undo is guessing.

## When a command can't reverse itself from its own state

Three escape hatches, in order of preference:

**Store the inverse command.** Works when the inverse is expressible as another
command of the same kind. A move's inverse is a move.

**Snapshot.** Keep a copy of the affected state before executing, and restore it
on undo. That's Memento, and the honest thing to say is "this is Memento, and
I'd use it when the action isn't cleanly invertible." It costs memory
proportional to what you snapshot, so snapshot the smallest thing that works.

**Compensate rather than undo.** For anything that touched the outside world you
cannot undo at all — you can only do something that makes up for it. A refund is
not the undo of a charge; it's a second, separate, forward action that leaves
both in the ledger. Getting this distinction right is the whole of
[hld/06-multi-step-processes](../../hld/06-multi-step-processes/), and saying it
in an LLD round shows you know where the boundary is.

## Redo, and the branch problem

Redo is a second stack. Execute pushes to undo and clears redo; undo pops from
undo and pushes to redo.

The clearing is the part people forget, and it matters: undo three moves, then
make a different move, and the three you undid are no longer reachable. Anything
else gives you a corrupt history where redo replays moves that no longer make
sense on the current board.

## The bridge worth mentioning

A Command is a **serialisable unit of work**. Serialise it and put it on a queue
and you've got a job. Write it to a log instead of applying it and you've got an
event-sourced system, where the current state is a fold over the command history
and undo is just replaying to an earlier point.

That's the same object doing LLD duty and HLD duty, and pointing it out mid-answer
is the kind of thing that moves an interview up a level. See
[hld/04-long-running-tasks](../../hld/04-long-running-tasks/).

## Command vs Strategy

Both wrap behaviour in an object, and they get confused.

A **Strategy** is *how* to do something, chosen by the caller, and it's usually
stateless and reused. A **Command** is *what to do*, including its arguments,
usually created fresh per action and often kept afterwards.

The tell: if you're storing it in a list after running it, it's a Command.

## Where it goes wrong

**Commands that reach for global state at undo time.** If `undo()` reads the
current board to work out what to restore, it will be wrong the moment two
commands touch the same square. Capture at execute, restore from the capture.

**Undo that isn't symmetric.** If `execute` has a side effect that `undo` doesn't
reverse — a counter incremented, an event published — the history lies. Either
make both symmetric or don't put that side effect in a command.

---

## Run it

```
./run.sh lld/13-command
```

Plays a short game with captures, undoes back through them, redoes, then shows
the redo stack being cleared by a divergent move. Ends with the same commands
serialised to a log and replayed onto a fresh board, which is the
event-sourcing point made concrete.

## Practice

| Problem | What to watch for |
|---|---|
| [Chess Game](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/chess-game.md) | Move, undo, and a full move history. Also good practice for modelling piece movement rules. |
| [Task Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/task-management-system.md) | Every mutation as a command, giving you undo and an audit trail for free. |
| [Digital Wallet Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/digital-wallet-service.md) | Transactions as commands with compensating actions — the LLD shadow of a saga. |

## Read

- [Refactoring Guru — Command](https://refactoring.guru/design-patterns/command)
- [AlgoMaster — Command](https://algomaster.io/learn/lld/command)
