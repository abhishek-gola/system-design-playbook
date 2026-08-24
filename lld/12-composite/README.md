# Composite

**The signal:** a tree, where one thing and a group of things have to be treated
identically.

**What it fixes:** callers writing `if (isLeaf)` everywhere they walk the
structure.

---

## A file system in eight lines

```java
interface Node { long size(); }

class File implements Node {
    private final long bytes;
    public long size() { return bytes; }
}

class Directory implements Node {
    private final List<Node> children = new ArrayList<>();
    public long size() { return children.stream().mapToLong(Node::size).sum(); }
}
```

The caller asks any node for its size and never checks what kind it is.
Recursion lives inside the composite, not in the client.

That's the property to name: **the client is flat even though the data isn't.**

## The question that always follows

Operations that only make sense on one type — `addChild` on a file. Two answers,
and there's no clean winner:

| Approach | Gain | Cost |
|---|---|---|
| `addChild` only on `Directory` | honest types; a file can't pretend | the client has to know which it holds, so the uniformity you came for is gone at exactly the moment you want to build the tree |
| `addChild` on `Node`, throwing on leaves | perfect uniformity for the client | a leaf advertises a method it can't honour, which is a Liskov violation you signed up for on purpose |

Say the trade-off out loud. Interviewers know there's no right answer and are
listening for whether you know it too.

The version here puts child management on `Directory` and keeps `Node` narrow,
because in an interview the client code is what gets read, and it's mostly
traversal rather than construction. If they push, the counter-argument is
Swing's `Component`, which took the other route.

## Where it earns its keep beyond the toy example

Anywhere a total is defined recursively:

- a menu of sections of items, priced uniformly
- an org chart where a manager's headcount includes their reports' reports
- nested comment threads rendered and counted the same way
- a UI component tree that lays itself out

If you can say "the total for a group is the same operation applied to its
parts", you have a composite.

## The traps

**Cycles.** Nothing in the pattern stops you adding a directory to itself, and
`size()` will then recurse until the stack goes. Real file systems have hard
links and symlinks and handle this with visited-sets. Mention it; you don't have
to implement it unless asked.

**Depth.** Recursion is the natural expression and it's fine at interview scale.
If the tree can be tens of thousands deep, say you'd convert to an explicit
stack. The demo does it recursively and says so.

**Caching sizes.** Tempting, and the moment you cache you own invalidation up
the whole parent chain. Only do it if asked, and then say the parent pointer is
what makes it possible.

## Composite and Visitor

They're a natural pair, and worth mentioning together. Composite gives you a
uniform tree; Visitor lets you add operations to it without editing every node
class. If the interviewer asks how you'd add ten different reports over this
tree without ten new methods on `Node`, Visitor is the answer.

See [lld/14-remaining-patterns](../14-remaining-patterns/) for the one-liner.

---

## Run it

```
./run.sh lld/12-composite
```

Builds a small tree, totals it, finds files by predicate, prints it with
indentation, deletes a subtree recursively, and demonstrates the cycle problem
being caught rather than blowing the stack.

## Practice

| Problem | What to watch for |
|---|---|
| [File System](https://refactoring.guru/design-patterns/composite/java/example) | Sizes, search, and a recursive delete. Then add symlinks and watch the model strain. |
| [Restaurant Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/restaurant-management-system.md) | Menus containing sections containing items, priced uniformly. |
| [Stack Overflow](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/stack-overflow.md) | Nested comment threads rendered and counted uniformly. |

## Read

- [Refactoring Guru — Composite](https://refactoring.guru/design-patterns/composite)
- [AlgoMaster — Composite](https://algomaster.io/learn/lld/composite)
