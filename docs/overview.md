# What is this project?

A non-technical introduction. There is no code in this document.
The only word you may not know is *branch*, and we'll define it
the moment we use it.

---

## 1. The one-sentence version

A research prototype that explores a smarter way to **test
computer programs** by letting the program "watch itself" being
tested and learning from what it sees.

---

## 2. Why we test software at all

Every program is full of small decisions. "If the user's age is
over 18, allow them in; otherwise refuse." "If the temperature is
above 30 degrees, turn on the fan; otherwise leave it off."

Each decision is a **branch** — two paths the program might take
depending on the situation. A real program has thousands of
branches. The job of testing is to make sure every branch behaves
correctly.

If even one branch is broken, the user notices: the fan turns on
at the wrong temperature, the underage user gets through, the bank
transfers the wrong amount.

---

## 3. How people test software today

### 3a. Example-based testing

A programmer thinks of a few specific situations, writes down what
the right answer should be, and checks the program against each
one. Works well — but only tests the cases the programmer thought
of.

### 3b. Property-based testing

Instead of writing specific examples, you describe a **rule** the
program should always obey, and let the computer try lots of
random inputs to see if the rule ever breaks. The computer thinks
of inputs the programmer would never have thought of.

---

## 4. The blind spot of random testing

Random inputs find common mistakes; they are bad at finding rare
ones.

Imagine a function: *"If the number is exactly 42, return `the
answer`. Otherwise, return `something else`."* The first branch is
only reached when the input is exactly 42 — virtually never if you
draw from billions of possibilities. The random tester reports "no
problems" — true, but only for the branches it walked through.

This is the **blind spot**: random testing can completely miss
branches and not know it missed them.

---

## 5. The missing piece: coverage

To fix the blind spot we first have to see it. **Coverage** is the
idea: while the program runs, an extra layer watches and tracks
which lines were touched.

Think of a museum map: every time a visitor walks into a room, we
tick it off. At the end of the day, untouched rooms tell us where
no one went. Coverage gives the same kind of map for code.

---

## 6. The big idea: use the map to guide the next input

If we know which branches we haven't reached, we don't have to
keep guessing randomly. We can ask: *"What kind of input would be
likely to reach that unvisited branch?"*

That is **coverage-guided** property-based testing. It is well
known in some corners of software security (AFL has used it for
years on C programs) but rare in everyday testing tools, and
especially rare in the Scala world. The goal of this thesis is a
small prototype that brings it into Scala and measures whether it
actually helps.

---

## 7. What this prototype does today

1. We point the framework at our catalogue of small methods.
2. For each method and each strategy, we ask an input generator for
   10000 inputs (integers, lists, options, tuples, or little trees, as
   the method needs).
3. For each input we run the method.
4. As the method runs, an extra layer records which lines were
   executed.
5. We collect all 10000 observations into a single picture of how
   well the method's branches were exercised over the session.
6. We write the result to disk (see §9).

**Random** samples uniformly and ignores past observations — it is
exactly what a ScalaCheck user gets today, and serves as the honest
baseline. On top of it the framework adds two **feedback
channels**, each attacking a different *kind* of hard branch:

- **Pool** mines the integer constants written in the method's own
  source and splices them into the draw — the reliable way to hit
  needle-in-a-haystack *literal* branches.
- **Mutation** keeps a list of "seeds" — inputs whose iteration
  covered a previously-uncovered branch — and mostly perturbs the
  most recent one (bump a number, drop a list tail, grow a tree).
  Because each "rung" of a structured target is its own branch, an
  input that climbed one rung is kept and nudged one step further —
  the reliable way to reach *structured* targets a random draw rarely
  stumbles on (sorted inputs, preserved tuple components, or tree shape).

These channels **compose**: **pool-mutation** switches both on at
once. They are **complementary** — the pool hits magic literals,
mutation climbs structure — so combining the two covers the most.
Validity gates (the input must first *parse* or pass a *checksum*)
are reached by neither today; that is the open frontier (see the
proposal).

The project is a **measuring stick**: the part that observes and
reports is identical across strategies; the part that picks the next
input is the thing under study.

---

## 8. The little examples we test

The catalogue is grouped not by input type but by the **kind of
problem** random testing runs into:

- **MagicLiterals** — exact integer/list/tree literals; the pool
  should win.
- **MutationTargets** — realistic structured inputs; mutation should
  usually beat random.
- **MixedTargets** — different hard arms belong to different tactics;
  the composite should cover the most.
- **NumericSearch** — narrow computed numeric branches; none of the
  current tactics should solve these reliably.
- **Calibration** — ordinary shallow branches; every strategy should
  behave like random and cover them easily.

The benchmarks live under `sut/src/main/scala/benchmark/`, one file per
category.

---

## 9. What comes out the other end

The engine writes one file per (method, strategy) pair — a small
JSON with everything observed during the session:

```
engine/reports/statistics/
└── <category>/          e.g. MagicLiterals
    └── <method name>/   e.g. sign
        └── <strategy>/  e.g. random, pool, mutation, pool-mutation
            └── seed=01/
                └── coverage.json   — raw measurement
```

`make analyze` then runs the Python script
([`engine/reports/scripts/compare.py`](../engine/reports/scripts/compare.py))
which reads those JSONs and writes:

- next to each `coverage.json`: a `coverage.svg` — the method's
  branch tree, leaves coloured by whether the strategy ever hit
  them;
- under `engine/reports/statistics/_summary/`: a horizontal bar
  chart per bench whose bars carry, beside each strategy's
  coverage %, the input that reached it and (for non-random
  strategies) the speed comparison against random; plus
  per-bench and suite-wide aggregate bars;
- and a small table (`significance.csv`) reporting, for each
  strategy, *how reliably* it beats random across the repeated
  runs — an effect size and a standard significance test — so
  "better" is a measured claim, not an impression.

The split is intentional: the engine produces the *measurement*,
the scripts produce the *presentation*. Either side can be
rewritten without touching the other.

---

## 10. Who cares?

People who test software, because better tools mean fewer bugs
reach real users. People who research testing methodology, because
coverage-guided testing is one of a small handful of techniques
that consistently outperforms pure random on certain kinds of
code, and bringing it to a new language is genuinely useful.
People who write Scala, because no widely-used coverage-guided
property-based testing tool exists for the language today.

The thesis question, in one line: *does coverage feedback make
random testing measurably better, and by how much, on small
methods with branchy structure?*
