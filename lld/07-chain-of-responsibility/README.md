# Chain of Responsibility

**The signal:** a request must pass a sequence of checks, the sequence should be
configurable, and any check can stop it dead.

**What it fixes:** a `validate()` method five hundred lines long that nobody will
ever reorder.

This is the pattern that maps most directly onto risk and payments work, so if
your background is there, this is the one to make your signature answer.

---

## A risk pipeline

```java
abstract class RiskCheck {
    private RiskCheck next;
    RiskCheck then(RiskCheck n) { this.next = n; return n; }

    final Decision handle(Txn t) {
        Decision d = evaluate(t);
        if (d.isTerminal()) return d;                    // blocked or explicitly allowed
        return next == null ? Decision.allow() : next.handle(t);
    }
    protected abstract Decision evaluate(Txn t);
}

velocityCheck.then(deviceFingerprintCheck)
             .then(blacklistCheck)
             .then(amountThresholdCheck)
             .then(mlScoreCheck);
```

Note `handle()` is `final` and `evaluate()` is abstract. That's a template method
sitting inside the chain: the traversal is fixed, only the decision varies. It
stops a subclass from forgetting to call `next`, which is the single most common
bug in a hand-rolled chain.

## The sentence that separates you from a textbook answer

**Order the chain by cost.** Cheap in-memory checks — blacklist lookup, amount
threshold — run before the network call to the ML scorer, so the expensive one
only ever sees traffic that survived everything else.

Then add the operational parts:

- each handler records **which rule fired**, so you can debug a false positive
  six weeks later without redeploying anything
- the chain is **loaded from config**, so risk analysts reorder it without a
  deploy
- a check that can't reach its dependency **fails open or closed explicitly** —
  and which one it is depends on the check, not on a global setting. Blacklist
  unavailable? Fail closed, it's cheap to retry. ML scorer timing out? Fail open,
  because a 200ms budget doesn't allow for waiting

Say that and you are describing a system somebody operates, not a pattern from a
book. If you have run something like it, this is the moment to say so.

## Three decisions, not two

A check returning `boolean` is the version that doesn't survive contact with
production. You need at least:

| Decision | Meaning | Chain behaviour |
|---|---|---|
| `BLOCK` | this is fraud | stop, terminal |
| `ALLOW` | explicitly whitelisted, skip the rest | stop, terminal |
| `CONTINUE` | nothing to say | pass to the next handler |
| `REVIEW` | suspicious, don't block, queue for a human | continue but remember |

`REVIEW` is the one worth adding unprompted. Real risk systems don't do
allow/block, they do allow/review/block, because the cost of a false positive on
a genuine customer is much higher than the cost of a human glance.

## The classic version

ATM cash dispensing: a ₹2000 handler passes the remainder to ₹500, which passes
to ₹200, then ₹100. Same structure, different domain, and it's the one they'll
reach for by default. It's implemented here too, including the
case everyone forgets — **the machine can't make the exact amount**, and you must
not dispense a partial withdrawal.

## Chain vs a list of validators

Honest question, and worth answering out loud before they ask.

A plain `for (Validator v : validators)` loop does most of this and is simpler.
Chain of Responsibility earns the extra structure when handlers need to
**decide whether the rest of the chain runs at all** — a whitelist hit that skips
everything downstream, or a handler that transforms the request before passing it
on.

If all your handlers do is vote, use a list and say so. Reaching for the heavier
pattern when the lighter one fits is marked down.

---

## Run it

```
./run.sh lld/07-chain-of-responsibility
```

Five transactions through the risk chain — clean, blocked on blacklist,
review-flagged on velocity, whitelisted, and blocked by the ML scorer — then the
chain reordered from "config" so you can see the expensive check stop being
called. Then the ATM dispenser, including a request it has to refuse.

## Practice

| Problem | What to watch for |
|---|---|
| [ATM cash dispenser chain](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/atm.md) **(core)** | Denomination handlers passing the remainder down. Handle the case where the machine can't make the exact amount. |
| [Logging Framework — level chain](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/logging-framework.md) | Debug passes to info passes to error. Same shape, different domain. |
| [Digital Wallet Service](https://github.com/ashishps1/awesome-low-level-design/blob/main/problems/digital-wallet-service.md) | Build the transaction validation chain: balance, limits, KYC status, velocity, blocklist. |

## Read

- [Refactoring Guru — Chain of Responsibility](https://refactoring.guru/design-patterns/chain-of-responsibility)
- [AlgoMaster — Chain of Responsibility](https://algomaster.io/learn/lld/chain-of-responsibility)
