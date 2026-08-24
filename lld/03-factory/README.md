# Factory, and its two cousins

**The signal:** a `new` keyword sitting inside business logic, right next to a
switch on a type field.

**What it fixes:** construction knowledge leaking everywhere. Move it to one
place so adding a type touches one file.

---

## Three things share this name — say which one you mean

Most candidates use all three words interchangeably. Interviewers notice when
you don't.

**Simple Factory.** A static method with a switch. Not a GoF pattern at all, and
what ninety percent of interviews actually want.

**Factory Method.** A base class defines `createX()` and each subclass decides
what to return. Use when the creating class is itself part of a hierarchy — the
classic being a `Dialog` whose subclasses each build their own kind of button.

**Abstract Factory.** A factory that produces a *family* of related objects that
must match each other.

```java
// Simple factory — the one you'll write
interface Notifier { void send(Message m); }

class NotifierFactory {
    static Notifier of(Channel c) {
        return switch (c) {
            case EMAIL -> new EmailNotifier(smtp);
            case SMS   -> new SmsNotifier(gateway);
            case PUSH  -> new PushNotifier(fcm);
        };
    }
}

// Abstract factory — a family that must stay consistent
interface PaymentProviderFactory {
    Charger charger();
    Refunder refunder();
    WebhookVerifier verifier();
}
class RazorpayFactory implements PaymentProviderFactory { ... }
```

## When abstract factory is justified, and when it's showing off

It earns its complexity only when **mixing families would be a bug**. A Razorpay
charger paired with a Stripe webhook verifier is nonsense — it would verify
signatures against the wrong secret and silently reject every callback. The
factory makes that combination unrepresentable.

If there's no such constraint, you're over-engineering, and saying so is worth
marks. "I'd use a simple factory here — there's no consistency requirement
between these objects, so an abstract factory would be ceremony" is a better
answer than producing one.

## The switch you're allowed to keep

People come out of an OCP lesson believing every `switch` is a defect. It isn't.
A factory is exactly where a switch belongs: it's the one place that's *supposed*
to know about every type, and confining it there is the point. What OCP objects
to is the same switch appearing in six other files.

If you want to remove even that one, register suppliers in a map at startup:

```java
Map<Channel, Supplier<Notifier>> registry = Map.of(
    Channel.EMAIL, () -> new EmailNotifier(smtp),
    Channel.SMS,   () -> new SmsNotifier(gateway));
```

Now a new channel is a new registry entry, loadable from config. Worth showing
if they push on OCP; don't lead with it, because the switch version reads more
clearly and clarity wins the first pass.

## Factory vs Builder

Both replace a raw constructor, and candidates blur them.

Factory answers **which class do I get**. Builder answers **how do I assemble
one class with a lot of optional parts**. If the return type varies, that's a
factory. If the return type is fixed and the parameter list is long, that's a
[builder](../04-builder/).

---

## Run it

```
./run.sh lld/03-factory
```

Shows the simple factory, the registry variant, and the abstract factory —
including a deliberate attempt to mix two providers, so you can see what the
abstract factory is preventing.

## Practice

| Problem | What to watch for |
|---|---|
| [Digital Wallet Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/digital-wallet-service.md) **(core)** | Payment instruments (card / UPI / netbanking / balance) behind one factory. |
| [Vehicle Rental System](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/car-rental-system.md) | Vehicle types plus the pricing that comes with each. Watch for where abstract factory is genuinely justified. |
| [Online Food Delivery Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/food-delivery-service.md) | Familiar territory. Resist putting everything on one factory. |

## Read

- [Refactoring Guru — Factory Method](https://refactoring.guru/design-patterns/factory-method)
- [Refactoring Guru — Abstract Factory](https://refactoring.guru/design-patterns/abstract-factory)
- [AlgoMaster — Factory Method](https://algomaster.io/learn/lld/factory-method)
