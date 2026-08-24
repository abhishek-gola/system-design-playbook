# State

**The signal:** an enum field plus if-else scattered across five methods, all
asking "what state am I in?" — or the requirements read as a lifecycle.

**What it fixes:** illegal transitions. With State they become impossible to
express, instead of being guarded by conditionals you have to remember to write.

---

## A vending machine as a set of classes, not a set of ifs

```java
interface VendingState {
    void insertCoin(Machine m, Coin c);
    void selectItem(Machine m, String code);
    void dispense(Machine m);
}

class IdleState implements VendingState {
    public void insertCoin(Machine m, Coin c) { m.addBalance(c); m.setState(new HasMoneyState()); }
    public void selectItem(Machine m, String code) { throw new IllegalStateException("insert coin first"); }
    public void dispense(Machine m)               { throw new IllegalStateException("nothing selected"); }
}
// HasMoneyState, DispensingState, OutOfStockState follow the same shape
```

Each state handles the transitions that are legal from where it stands, and
refuses the rest. There is no central switch. Adding a `MaintenanceState` is a
new file and nothing else.

## Draw the diagram first

States as boxes, events as labelled arrows. Interviewers grade that diagram — it
proves you found the transitions before you started typing, and it makes the
missing ones obvious.

```
        insertCoin                selectItem (in stock)
  IDLE ────────────► HAS_MONEY ─────────────────────────► DISPENSING
    ▲                  │  │                                    │
    │  refund          │  │ selectItem (sold out)              │ dispense
    └──────────────────┘  │                                    │
    ▲                     ▼                                    │
    │                 OUT_OF_STOCK ──── refund ────────────────┤
    └───────────────────────────────────────────────────────────
                         (change returned, item dropped)
```

The transition candidates forget is the **refund path** from `HAS_MONEY` back to
`IDLE`. Ask about it unprompted — "what happens if they change their mind after
putting money in" is a requirements question, and asking it in minute three is a
senior move.

## Where the transition logic lives

Two schools, and you should have an opinion with a reason attached.

**Each state decides its own next state** (what's implemented here). Easy to
extend — a new state is a new file, and you never touch the existing ones. Harder
to audit, because the full transition map only exists by reading every class.

**A central transition table** mapping `(state, event) -> state`:

```java
Map<StateKey, StateName> table = Map.of(
    new StateKey(IDLE,      INSERT_COIN), HAS_MONEY,
    new StateKey(HAS_MONEY, SELECT_ITEM), DISPENSING,
    new StateKey(HAS_MONEY, REFUND),      IDLE);
```

Easier to audit, easy to load from config, easy to render as a diagram
automatically. Harder to attach behaviour to, because the actions have to live
somewhere else.

Rule of thumb: table when the transitions are the interesting part (a workflow
engine, an order lifecycle that business people want to see), classes when the
behaviour in each state is the interesting part (a vending machine, a game).

## State vs Strategy

Identical structure, different intent, and getting this wrong is a common tell.

- A **Strategy** is chosen from outside and never changes itself. It usually has
  no reference back to its context.
- A **State** changes itself. It almost always holds or receives the context,
  because it needs to set the next state.

If you can't tell which you're looking at, ask: who decides what comes next? The
caller, or the object?

## The interview trap

Don't put `Machine` state transitions in `Machine`. The whole gain is that
`Machine` becomes dumb — it holds the balance, the inventory, and a reference to
the current state, and delegates every event. If `Machine` still has a switch in
it, you've written the enum version with extra classes.

---

## Run it

```
./run.sh lld/06-state
```

A full happy path, then every illegal transition attempted in turn so you can
see each state refusing exactly what it should, then a refund, then a sold-out
item.

## Practice

| Problem | What to watch for |
|---|---|
| [ATM](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/atm.md) **(core)** | Card inserted, PIN entered, amount selected, dispensing, ejecting. Heavy on states, and it rehearses the payment vocabulary. |
| [Elevator System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/elevator-system.md) **(core)** | State plus a scheduling strategy. Two patterns in one problem, which is how real questions arrive. |
| [Vending Machine](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/vending-machine.md) | The canonical version. Code it cold after reading nothing. |

## Read

- [Refactoring Guru — State](https://refactoring.guru/design-patterns/state)
- [AlgoMaster — State](https://algomaster.io/learn/lld/state)
- [State machine diagram](https://algomaster.io/learn/lld/state-machine-diagram)
