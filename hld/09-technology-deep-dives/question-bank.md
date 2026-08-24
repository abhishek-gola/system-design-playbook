# Question bank

Every question from the [README](README.md), flat, with nothing else on the
page. Read one, answer it out loud as though someone is listening, tick it off.
Do not read the notes in the README first — the point of this page is to find
out what comes out under no warning.

Anything you hedge on, waffle through, or answer with a config setting instead
of a mechanism goes back into the template file for that technology. Run the
whole list once a week in the fortnight before a loop; it takes about twenty
minutes out loud.

## Kafka

- [ ] If I publish two events for the same order ID, am I guaranteed to read them in that order?
- [ ] One of your consumers dies mid-batch. Walk me through what happens.
- [ ] You've said the pipeline is exactly-once. Exactly-once between which two points?
- [ ] When would you use a compacted topic rather than just setting a long retention?
- [ ] What exact configuration stops you losing an acknowledged write, and what does it cost you?
- [ ] Your lag alarm is firing. Take me through the first ten minutes.
- [ ] Tell me about a time Kafka broke in production. What did you change?

## Flink

- [ ] Why does Flink make you choose a time semantic at all? Which one do you use, and why?
- [ ] An event turns up an hour late. What happens to it?
- [ ] What changes when your keyed state stops fitting in memory?
- [ ] How does a checkpoint work, and how does that become end-to-end exactly-once?
- [ ] The job is slow. How do you tell a slow sink from a hot key?
- [ ] Tell me about a time a Flink job broke in production. What did you change?

## Redis

- [ ] Redis is single-threaded. Why is it still fast, and when does that hurt you?
- [ ] Which structure would you use for this, and why not a sorted set?
- [ ] The Redis box hard-reboots. How much do you lose?
- [ ] Why can't I run MULTI across these two keys?
- [ ] How do you make a read-modify-write atomic?
- [ ] Would you use Redlock for a distributed lock?
- [ ] Tell me about a time Redis broke in production. What did you change?

## DynamoDB

- [ ] Model this access pattern in a single table.
- [ ] GSI or LSI here, and what does each cost you in consistency?
- [ ] One customer is a large share of your traffic. What happens?

## The two that decide the round

- [ ] Pick anything on my CV. Explain it in five lines, name one alternative, and tell me when you'd pick the alternative.
- [ ] Which of these have you actually operated, and which have you only used?
