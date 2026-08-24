# Adapter

**The signal:** a third party's interface doesn't match yours, and you don't
want their vocabulary leaking into your domain.

**What it fixes:** vendor types spreading through business logic, which is how a
provider migration turns into a six-month project.

---

## One interface, many gateways

```java
interface PaymentGateway {
    ChargeResult charge(Money amount, Instrument i, IdempotencyKey k);
}

class RazorpayAdapter implements PaymentGateway {
    private final RazorpayClient sdk;
    public ChargeResult charge(Money a, Instrument i, IdempotencyKey k) {
        RzpOrder o = sdk.orders().create(toRzpRequest(a, i, k));   // translate in
        return toDomain(o);                                        // translate out
    }
}
```

Everything Razorpay-shaped stops at the adapter boundary. Your `OrderService`
knows about `Money` and `ChargeResult` and nothing else.

## The translation goes both ways, and the return trip is the hard one

Candidates get the request translation right and then hand the vendor's
response object back to the caller, which defeats the entire exercise. The
adapter has to map:

- **the happy path** — vendor's order object to your `ChargeResult`
- **the error codes** — Razorpay's `BAD_REQUEST_ERROR` and Stripe's
  `card_declined` both become your `DECLINED`, and a vendor code you've never
  seen becomes `UNKNOWN` rather than leaking through
- **the units** — one provider takes paise, another takes rupees with decimals,
  and getting this wrong is a hundred-fold billing error rather than a crash

That last one is worth saying out loud. Money in a domain type with an explicit
currency and a minor-unit integer is the sort of detail interviewers notice,
because everyone who has actually shipped payments has been bitten by it.

## Two things worth saying unprompted

**The adapter is your test seam.** A fake implementation of `PaymentGateway`
means your order tests never touch the network, never need a sandbox account,
and can simulate a decline on demand. Without the interface you are stuck
mocking an SDK class you don't own.

**The adapter is your failover.** If Razorpay is down you route to the next
adapter and no business code changes. That's a resilience argument coming from
an LLD candidate, which is unusual enough to be memorable.

The demo implements both.

## Adapter vs Facade vs Anti-corruption layer

| | Purpose |
|---|---|
| **Adapter** | make an incompatible interface fit one you already have |
| **Facade** | put a simple front on a complicated subsystem you own |
| **Anti-corruption layer** | the same idea as adapter, applied to a whole bounded context rather than one class — the DDD name for it |

If the interviewer uses the phrase "anti-corruption layer", they're testing
whether you know it's the same instinct at a larger scale. Say so.

## Object adapter vs class adapter

The version here is an **object adapter**: it holds the adaptee as a field. A
class adapter inherits from the adaptee instead, which Java only half supports
(single inheritance) and which couples you to the vendor's class hierarchy.

Prefer composition. If asked why, the answer is that you can adapt a `final`
class, adapt several objects into one interface, and swap the adaptee at
runtime — none of which inheritance gives you.

## Where it goes wrong

An adapter that grows business logic. The moment your `RazorpayAdapter` starts
deciding whether to retry, or applying a discount, it isn't an adapter any more
and that logic is now duplicated in every provider you support. Translation
only. Decisions live above the interface.

---

## Run it

```
./run.sh lld/09-adapter
```

Charges through two providers with genuinely different SDK shapes, maps both
error vocabularies onto one, fails over when the primary is down, and runs an
order test against a fake with no network in sight.

## Practice

| Problem | What to watch for |
|---|---|
| [Digital Wallet Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/digital-wallet-service.md) | Several payment service providers behind one gateway interface, with failover. |
| [Ride-Sharing Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/ride-sharing-service.md) | Swap map and routing providers without the matching engine noticing. |
| [Logging Framework](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/logging-framework.md) | Adapt third-party sinks — Datadog, CloudWatch, Kafka — to your `Sink` interface. See [lld/01-solid](../01-solid/), which already has the `Sink` this would plug into. |

## Read

- [Refactoring Guru — Adapter](https://refactoring.guru/design-patterns/adapter)
- [AlgoMaster — Adapter](https://algomaster.io/learn/lld/adapter)
