# System design playbook

Two tracks, LLD and HLD, arranged the way a DSA sheet is: a pattern, the signal
that tells you to reach for it, one worked example in code, and problems to
practise on. The web version of the same sheet lives in [web/design-sheet.html](web/design-sheet.html)
if you want the checkbox tracker — open it in a browser, your ticks are saved locally.

The folders are the real thing. The HTML is a progress tracker on top of them.

## Layout

```
lld/    16 folders, one per pattern, in study order
hld/    11 folders, one per pattern group, mirroring the sheet's steps
web/    the HTML sheet
```

Every folder has a `README.md` with the explanation and, where code makes the
point better than prose, `.java` files you can run.

## Running the code

There is no Maven, no Gradle, no dependencies. Each folder is a flat set of
plain `.java` files with no package declaration, and exactly one class with a
`main` called `Demo`.

```
./run.sh lld/02-strategy
./run.sh hld/07-aggregation-and-counting
./run.sh                       # lists every folder that has runnable code
```

You need a JDK 17 or newer. This machine doesn't have one yet:

```
brew install openjdk@21
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \
             /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

If you'd rather not use the script, `javac -d /tmp/out lld/02-strategy/*.java && java -cp /tmp/out Demo`
does the same thing.

## How to actually use this

Reading a pattern folder takes ten minutes and teaches you almost nothing. The
sequence that works is:

1. Read the README. Close it.
2. Write the pattern from scratch in a blank file. Not from memory of the code —
   from memory of the *problem it solves*.
3. Run your version, then diff your thinking against the folder's version. The
   places you differ are the lesson.
4. Do one practice problem from the README's list, on a clock.

Step 2 is the one people skip and it's the only one that counts. Recognising a
design and generating one under time pressure are different skills, and only the
second is being tested.

## The two tracks

- [lld/README.md](lld/README.md) — patterns, SOLID, concurrency, and the
  fifty-minute machine-coding shape.
- [hld/README.md](hld/README.md) — the delivery framework, the seven scaling
  patterns, resume deep dives, and your signature design.

## Credit

The problem lists point at [awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design),
[Hello Interview](https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction),
[AlgoMaster](https://algomaster.io) and [Refactoring Guru](https://refactoring.guru/design-patterns).
The code here is written out rather than linked so you can run it, break it, and
rewrite it.
