# Builder

**The signal:** a constructor with more than about four parameters, several of
them optional — or you need the object to be immutable once built.

**What it fixes:** telescoping constructors, and objects that can exist in an
invalid half-built state.

---

## An order that can't be built wrong

```java
Order o = new Order.Builder(customerId)
        .addItem(biryani, 2)
        .withCoupon("SAVE50")
        .deliverTo(address)
        .scheduledFor(slot)
        .build();               // validation happens here

// Order itself has no setters. Once built, it's frozen.
```

The part that scores is `build()`. That's where "a coupon requires cart value
above ₹199" and "scheduled slots must be at least 30 minutes out" belong — one
place, checked once, before the object exists.

Scatter those checks across setters and you get an object that's valid halfway
through and invalid at the end, which is worse than not checking at all because
it looks like it works.

## Why this beats the alternatives

**Telescoping constructors.** `Order(a)`, `Order(a,b)`, `Order(a,b,c)`… Six
overloads, and the caller writes `new Order(id, items, null, null, address, null)`
with no idea which null is which.

**Setters on a mutable object.** Now the object is never final, any code
anywhere can change it after validation, and there is no moment at which you can
say "this is definitely valid".

**A parameter object.** Genuinely fine for three or four fields. It just moves
the problem when there are ten.

## Two variants worth knowing

**Static nested builder** (Effective Java style) — what's implemented here and
what you'll write in Java. Required fields go in the builder's constructor,
optional ones in `withX()` methods.

**Step builder** — forces the required fields first by returning a *different
interface* at each step, so the compiler won't let you call `build()` early:

```java
interface CustomerStep { ItemStep forCustomer(String id); }
interface ItemStep     { BuildStep addItem(Item i, int qty); }
interface BuildStep    { BuildStep withCoupon(String c); Order build(); }
```

Mention it only if they push. It's more type-safe and considerably more code,
and in an interview the extra ceremony usually costs more than it earns.

## Don't reach for it too early

A three-field object with a plain constructor doesn't need a builder, and adding
one is exactly the kind of over-engineering interviewers mark down. The trigger
is **optionality plus immutability**, not size alone.

Java records are the honest answer for a plain immutable data carrier. Builders
start earning their keep when there are optional fields, when there are
cross-field invariants, or when the object is assembled across several call
sites.

## Builder vs Factory

Factory answers *which class do I get*. Builder answers *how do I assemble one
class with a lot of optional parts*. Varying return type means factory; fixed
return type with a long parameter list means builder.

---

## Run it

```
./run.sh lld/04-builder
```

Builds a valid order, then tries four invalid ones and prints why each was
refused. Note that every failure happens at `build()` — there is no moment where
a broken `Order` exists.

## Practice

| Problem | What to watch for |
|---|---|
| [Airline Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/airline-management-system.md) | An itinerary with optional legs, seat preferences and meal choices is the textbook case. |
| [Concert Ticket Booking System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/concert-ticket-booking-system.md) | Booking objects with lots of optional extras; validate the whole thing in `build()`. |
| [Restaurant Management System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/restaurant-management-system.md) | Orders with modifiers, and the invariants that only make sense once the order is complete. |

## Read

- [Refactoring Guru — Builder](https://refactoring.guru/design-patterns/builder)
- [AlgoMaster — Builder](https://algomaster.io/learn/lld/builder)
