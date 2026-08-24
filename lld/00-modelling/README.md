# Modelling, before any pattern

Two thirds of a bad LLD answer is lost before a single pattern shows up. This
folder is the grammar; the rest of the track is vocabulary.

Two things live here because they're both pre-pattern skills: getting the object
model right, and getting the clock right.

---

## Part 1 — Class relationships and the diagram

**The signal:** you've been handed a paragraph of English and you need nouns,
verbs, and lines between them.

**What it fixes:** every LLD round starts here, and interviewers form their
opinion of you in the first fifteen minutes — long before you write a pattern.

### Parking lot, modelled with zero patterns

Pull the nouns out first: `ParkingLot`, `Floor`, `Spot`, `Vehicle`, `Ticket`,
`Gate`, `PricingRule`. Now the only question that matters — what kind of line
joins each pair?

- **Composition** (filled diamond): the part dies with the whole. A `Floor` has
  no meaning without its `ParkingLot`. A `Spot` has none without its floor.
- **Aggregation** (hollow diamond): the part outlives the whole. A `Vehicle`
  exists before it parks and after it leaves.
- **Association**: two objects reference each other but neither owns the other.
  A `Ticket` points at a vehicle and a spot.
- **Dependency** (dashed): you only touch it inside a method. `Gate` uses a
  `PricingRule` to compute a fee; it doesn't hold one as a field.

Here's why this isn't academic. If `Spot` holds a reference to the parked
`Vehicle`, you have one design. If the `Ticket` holds both the vehicle and the
spot, you have a different one — and only the second supports several entry
gates issuing tickets at once without the gates talking to each other. The line
you draw *is* the design decision.

The code in this folder takes the second option, and the comments say where the
first one would have led.

### The test that settles most arguments

Ask: if I delete the container, should this object be deleted too? Yes means
composition. No means aggregation. If you can't answer, you haven't understood
the domain yet, and that's worth saying out loud rather than guessing.

### Inheritance vs composition

An `ElectricCar extends Car` is a trap in almost every interview problem. Fuel
type is a property, not a subtype. Reach for inheritance only when the subtype
genuinely has different *behaviour*, and even then check whether an injected
strategy would do it better.

The parking lot here has one `Vehicle` class with a `VehicleType` enum, not a
four-class hierarchy. Adding motorbikes is a new enum constant and a new row in
the size table. With inheritance it would be a new class, a new spot subclass,
and an edit to every switch that ever mentioned a vehicle.

### The diagram, in text

```
ParkingLot ◆─── Floor ◆─── Spot
     │                       ▲
     │                       │ (association: the ticket points at the spot)
     └──── Ticket ───────────┘
              │
              └──── Vehicle        (aggregation: the vehicle outlives the visit)

Gate ┈┈> PricingRule               (dependency: used inside a method, not held)
```

---

## Part 2 — The fifty-minute shape

**The signal:** the interviewer says "design a ride-hailing system" and then
goes quiet.

**What it fixes:** freezing, or the more common failure — spending thirty
minutes on requirements and shipping two classes.

### The clock

| Minutes | Phase | What belongs here |
|---|---|---|
| 0–8 | Requirements | Five questions, no more. Write the answers where the interviewer can see them. Then state two non-functional ones yourself — concurrency? persistence? scale? — because nobody volunteers those and asking makes you look senior. |
| 8–15 | Entities and relationships | Say the object model out loud while you draw it. This is the checkpoint: get agreement here or you'll code the wrong thing. |
| 15–22 | Interfaces and signatures | Method names and return types only, no bodies. If a signature feels awkward, the model is wrong and this is the cheapest moment to fix it. |
| 22–45 | Code, top down | Interfaces first, then the two or three classes with real logic. Stub the boring ones out loud: "I'd have a standard repository behind this, let me skip it unless you want to see it." |
| 45–50 | The extension | "How would you add X?" This is the question they're actually scoring, and a design built around the right pattern answers it in one sentence. |

### Two hard rules

Never start coding before minute 15. Never still be designing at minute 25. Set
a timer on every practice run until the pacing is automatic — it's the single
highest-return habit on this sheet.

### Narrate

Silence reads as being stuck even when you're thinking clearly. Say what you're
weighing, even if it's "I'm deciding whether pricing belongs on the ticket or
the gate, and I'm leaning gate because pricing rules change more often than
tickets do."

---

## Run it

```
./run.sh lld/00-modelling
```

The demo parks a bike and two cars, watches the second car take the only truck
bay because nothing smaller is free, and then turns a truck away. That last bit
is not a contrivance — smallest-fit allocation is greedy and starves large
vehicles, and noticing it unprompted is exactly the kind of thing that separates
a modelled design from a copied one.

No patterns anywhere in the code, which is the point. When you've read it, do a
second pass on paper and mark where a pattern would actually earn its keep.
(Answers: pricing wants Strategy, spot allocation wants a second Strategy for
the reason above, and the spot-status transitions are a very small State
machine. Nothing else.)

## Practice

| Problem | What to watch for |
|---|---|
| [Parking Lot](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/parking-lot.md) **(core)** | Model it with no patterns at all first. Add them on a second pass and notice which ones the design asked for. |
| [Library Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/library-management-system.md) | Pure modelling exercise. `Book` vs `BookCopy` is the distinction most people miss. |
| [Hotel Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/hotel-management-system.md) | `RoomType` vs `Room`, and where the booking window lives. |

Timed runs for the pacing half: [Vending Machine](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/vending-machine.md) **(core)**,
[Tic Tac Toe](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/tic-tac-toe.md),
[Traffic Signal](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/traffic-signal.md).

## Read

- [Class diagram notation](https://algomaster.io/learn/lld/class-diagram)
- [Association / aggregation / composition](https://algomaster.io/learn/lld/composition)
- [How to answer an LLD problem](https://blog.algomaster.io/p/how-to-answer-a-lld-interview-problem)
- [Hello Interview — how to prepare for LLD](https://www.hellointerview.com/blog/how-to-prepare-lld)
