# Real-time updates

**The signal:** the client needs to know within seconds, without polling for it.

**What it fixes:** polling at a frequency that's either too slow to feel live or
too expensive to run.

Chat, notifications, live comments, presence, collaborative editing. One pattern
underneath, several difficulty levels on top, and the difficulty is almost never
in the transport.

---

## Worked: WhatsApp

Start with the transport ladder and justify the rung you land on. Climbing it out
loud is worth more than jumping straight to WebSockets, because the climb shows
you know what each rung costs and the jump only shows you know a word.

| Transport | Direction | Reach for it when | What it costs you |
|---|---|---|---|
| **Polling** | client asks | a dashboard that refreshes every 30 seconds, or a client you don't control | wasteful at any interval short enough to feel live |
| **Long polling** | client asks, server holds | you want push semantics with no new infrastructure | a held request per client, and reconnect churn after every message |
| **Server-sent events** | server to client | notifications, live feeds, a stock ticker — anything one-directional | no client-to-server channel, so writes still go over normal HTTP |
| **WebSocket** | both ways | chat, collaborative editing, anything with a fast write path | you now own connection state, and that is the whole rest of this page |

The honest summary: SSE is underrated and gets you most of the way for feeds and
notifications, WebSockets are correct for chat, and long polling is a perfectly
respectable answer that nobody gives because it sounds unambitious.

## The hard part isn't the socket

It's routing. User B has a connection open on server 7. User A's message lands on
server 3, because a load balancer put A there and neither user had any say in it.
How does server 3 find server 7?

There are two answers worth having.

**A connection registry.** Redis holding `userId → serverId`, written on connect,
deleted on disconnect, and refreshed by a heartbeat so it carries a TTL.

```
connect     SET conn:{userId} {serverId} EX 30
heartbeat   SET conn:{userId} {serverId} EX 30     every 10s, per open socket
disconnect  DEL conn:{userId}
send        GET conn:{userId}  ->  forward to that server over an internal channel
```

The TTL is the part to defend. A server that dies without running its shutdown
hook never deletes anything, so without expiry its users point at a corpse
forever and stay undeliverable until a human notices. With expiry, the entry rots
on its own: live servers keep saying "still here", a dead one says nothing, and
thirty seconds later the cluster has forgotten it.

**Consistent hashing on userId.** Every server holds the same ring, so any of
them computes the owner of a user id locally with no lookup at all. No Redis on
the hot path, no cache to go stale.

The comparison is the interesting bit, and the mistake is presenting the ring as
strictly better:

| | Registry in Redis | Consistent hashing |
|---|---|---|
| Cost per message | one lookup, cacheable, sub-millisecond | zero |
| Who decides where a connection lives | the load balancer, and you record it | the ring, and you have to make it true |
| When a server dies | entries expire by TTL, users reconnect anywhere | the ring reassigns, but the surviving sockets don't move |
| Extra moving parts | Redis, and it becomes a hard dependency | ring membership has to agree across the fleet |
| Honest failure mode | a stale route for up to one TTL | a client connected to a node the ring no longer says owns it |

The ring tells you where a connection *should* be. It does not open the
connection. A phone dials in through a load balancer that knows nothing about
your ring, so either you add a routing tier that redirects clients to the node
the ring picked, or you accept that the ring and reality can disagree — which is
the same stale-route problem, minus the TTL that solved it.

For chat I would take the registry and pay for the lookup. Consistent hashing
earns its place where the *server* decides the assignment rather than the client:
partitioned stream consumers, a sharded cache, a scheduler handing out work. Say
which one you'd pick and give that reason; having a preference is most of the
mark.

The registry is a hint, never a guarantee. Between a crash and the TTL expiring,
lookups still return the dead server, so the delivery path has to cope with being
pointed at the wrong place. In the code here that's a two-line branch that treats
a stale route exactly like an offline user, which is the right size for it.

## The four follow-ups, in the order they come

**Offline delivery.** Messages persist in a per-user inbox with an undelivered
cursor, and a push notification through APNs or FCM is the fallback. The ordering
inside the send path is what matters: persist first, deliver second. Push first
and the message existed only inside a socket write, so a server that dies
mid-delivery loses it with no trace. Once it's durable, delivery is allowed to
fail, and that is what makes the crash case boring.

The cursor is an index into the log, not a flag per message. A reconnecting
client sends nothing but its user id and the server replays from the cursor. Keep
the client dumb here — a client that tracks "last message I saw" and asks for
everything after it is a client that can lie, and a client on a phone that has
been off for a week is a client whose state you should not trust.

**Ordering.** Monotonic sequence numbers per conversation, not wall-clock
timestamps. Two servers whose clocks differ by 40ms will disagree about which
message came first, and they will disagree about the one message where it
matters. Per conversation rather than global, because a global counter is a
contention point that buys nothing: nobody cares how your messages to Bob
interleave with two strangers talking in Peru.

There's a subtlety worth having ready. When a user comes back online you must
drain the whole backlog before pushing anything new, otherwise a live message
overtakes a queued one and the conversation reads out of order on the device. The
code here drains the inbox on every successful push for exactly this reason.

**Duplicates.** Client-generated message IDs, so a retry after a flaky network is
idempotent. The id has to come from the phone, before the message is sent. A
server-generated id cannot work, because the client has no way to tell a lost
request from a lost response — it retries either way, and a server-side id makes
the second attempt look like a brand new message.

The server keeps a dedup table of ids it has accepted. It doesn't need to be
forever; a few minutes covers every realistic retry window, and a TTL on that
table is fine.

**Multiple devices.** One user resolves to N connections, so the registry maps
`userId → set of (deviceId, serverId)` and delivery fans out to all of them. Read
state is the harder half: your laptop and your phone are at different points in
the same log, so the cursor becomes one per device rather than one per user, and
"Alice read this" has to sync across devices as its own event. This is where
interviewers go when they want to see whether your inbox model was actually
designed or just asserted.

## Group chat is a different problem

A 1:1 conversation is a routing problem. A 100,000-member group is a fan-out
problem, and the answer looks closer to a feed than to a chat: you stop pushing
per recipient and start thinking about write fan-out versus read fan-out, exactly
as in [hld/01-scaling-reads](../01-scaling-reads/). Notice which one you've been
asked before you start designing, because the two answers share almost nothing.

The rule of thumb that survives contact: fan out on write for small groups,
because pushing to 50 sockets is nothing; fan out on read for large ones, because
pushing to 100,000 sockets on every message is a self-inflicted denial of
service. Live comments on a stream are the extreme case — one writer, a million
readers, and the sane design pushes to a pub-sub topic per stream and lets the
edge handle multiplexing.

## The trade-off to name out loud

Delivery guarantees. You cannot have exactly-once over a network, so pick your
side and say which:

- Advance the cursor when you *send* and you get at-most-once — a socket that
  dies between the write and the phone loses that message silently.
- Advance it when the device *acknowledges* and you get at-least-once — a
  reconnect can replay a message the user already saw.

Chat picks at-least-once every time, because showing a message twice is
embarrassing and losing one is a bug report. Client-side deduplication on the
message id cleans up the duplicates, which is a second reason those ids are worth
having.

## The common mistake

Spending the first ten minutes on WebSocket handshakes, heartbeats and reconnect
backoff. All of that is real and none of it is what's being assessed. The
interviewer wants to hear "server 3 has to find server 7" within two minutes of
you choosing WebSockets, because the moment you say "the server holds connection
state" you have created a routing problem, a failure-recovery problem and a
capacity problem, and those are the three things worth the whiteboard.

The second mistake is treating the registry as the source of truth. It's a cache
of something living in another process's memory, and that process may have died a
millisecond ago. Design the delivery path so a wrong answer costs you a fallback,
not a lost message.

## Where else this shows up in the repo

- [lld/05-observer](../../lld/05-observer/) is this pattern in one process: a
  publisher, per-subscriber bounded queues, and an explicit policy for what
  happens when one falls behind. The vocabulary transfers directly — an inbox is
  a subscriber queue, and a device that has been off for a week is consumer lag.
- [hld/04-long-running-tasks](../04-long-running-tasks/) is the other half of the
  push story. When the work behind an update takes minutes rather than
  milliseconds, the client is not waiting on a socket, it's waiting on a job.

---

## Run it

```
./run.sh hld/03-realtime-updates
```

Eight sections against a hand-cranked clock, so the thirty-second registry TTL
expires between two printed lines and the output is identical on every machine:
a message routed across servers via the registry, a message to an offline user
landing in an inbox behind a cursor, sequence numbers ordering a conversation
whose halves arrive on different servers, a retry with a reused client message id
being absorbed, a server crashing and the registry confidently pointing at it,
the TTL sweeping the corpse away, the user reconnecting elsewhere and draining
the backlog in order, and the consistent-hashing alternative for contrast.

## Practice

| Problem | What to watch for |
|---|---|
| [Design WhatsApp](https://www.hellointerview.com/learn/system-design/problem-breakdowns/whatsapp) **(core)** | The anchor. Connection routing, offline delivery, ordering, multi-device. |
| [Design Facebook Live Comments](https://www.hellointerview.com/learn/system-design/problem-breakdowns/fb-live-comments) **(core)** | Massive fan-out to viewers of one stream. Different shape from chat. |
| [Design Google Docs](https://www.hellointerview.com/learn/system-design/problem-breakdowns/google-docs) | Collaborative editing, so operational transforms or CRDTs. The hardest one in this group. |

## Read

- [Pattern — real-time updates](https://www.hellointerview.com/learn/system-design/patterns/realtime-updates)
- [Long polling vs WebSockets](https://blog.algomaster.io/p/long-polling-vs-websockets)
- [Read: real-time messaging at Slack](https://slack.engineering/real-time-messaging/)
