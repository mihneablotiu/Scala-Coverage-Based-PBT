# Architecture

Technical companion to [`overview.md`](overview.md). The overview
explains *what* the project does in plain terms; this explains *how*
it is built.

---

## 1. Two subprojects

| Subproject | Role |
|------------|------|
| `sut`    | System under test — small benchmark methods, one file per *kind of problem* random PBT faces (`Saturated`, `MagicConstants`, `NarrowRanges`, `Relational`, `StructuralInvariants`, `DeepConditionals`, `StagedValidity`) plus a `Tree` type. Compiled with scoverage instrumentation. |
| `engine` | The `pbt` framework and an `app` experiment harness. |

---

## 2. Using the framework — one call

```scala
val pbt    = new Pbt(Paths.get("sut"))                // reads the instrumented SUT once
val report = pbt.check[Int](source, "classify", Strategy.coverageGuided) { n =>
  MagicConstants.classify(n); true                    // the property
}
```

You give a **method** (its source file + name), a **property** over
its input type, and a **strategy**. `check` generates inputs,
runs the property on each while measuring coverage, and returns a
[`Report`](../engine/src/main/scala/pbt/Report.scala). The input type
needs a [`Generatable`](../engine/src/main/scala/pbt/gen/Generatable.scala)
in scope (all primitives, collections and tuples are built in).

`random` is *literally* `Prop.forAll(arbitrary)` — stock ScalaCheck.
Every other strategy runs the identical loop and only adds
coverage-guided tactics, so the baseline is the real tool, not an
approximation.

---

## 3. The one idea: strategies are sets of tactics

A **tactic** is the whole vocabulary, behind one tiny interface
([`Tactic`](../engine/src/main/scala/pbt/strategy/Tactic.scala)):

```scala
trait Tactic[A] {
  def propose(fb: Feedback[A]): Option[Gen[A]]   // bias the next draw, or None
  def observe(input: A, fb: Feedback[A]): Unit   // learn from the input just run
}
```

A [`Strategy`](../engine/src/main/scala/pbt/strategy/Strategy.scala)
is just a **set of tactics**. Each draw mixes the active tactics'
proposals with a plain random draw (equal weights, so random stays a
healthy escape hatch). There are three tactics, each reading the live
coverage, each independent and composable:

- **Pool** — inject the literals that still-uncovered branches need.
- **Mutation** — perturb a corpus seed (an input that grew coverage).
- **Gradient** — hill-climb the branch distance to the nearest
  uncovered leaf.

So the **eight strategies are exactly the eight subsets of
`{Pool, Mutation, Gradient}`** — `random` is the empty set, the
all-three composite is the full set. Adding a tactic is: implement
`Tactic`, add a `Kind`, wire it in `Tactic.of`; nothing else changes.

![Feedback tactics are complementary](images/mechanisms.png)

The tactics are **complementary** — `compareInts` (a relation) is
reached only by the gradient, `accessLevel` (a magic string) only by
the pool, `magnitude` (`NaN`) only by mutation — so the all-three
composite covers the most.

---

## 4. The loop

![All four tactics combined](images/combined.png)

Each tactic combined with the random baseline is drawn on its own —
[pool](images/pool.png), [mutation](images/mutation.png),
[gradient](images/gradient.png) — and [`sources.png`](images/sources.png)
contrasts the single random source of stock ScalaCheck with this engine's
four.

The driver is ScalaCheck's own (`Test.check` over a
`Prop.forAllNoShrink` whose generator is `Gen.delay(...)` re-read each
draw) — not a hand-rolled fold, which is what lets `random` *be* plain
ScalaCheck. Per input:

1. **draw** — mix the active tactics' proposals with a random draw;
2. **run** the property (guarded, so a thrown exception still counts);
3. **read** the offsets scoverage fired in the method's file;
4. **mark** the leaves those offsets land inside as covered;
5. **record** into [`Feedback`](../engine/src/main/scala/pbt/strategy/Feedback.scala)
   and let each tactic `observe` the input.

`Feedback` is the single running signal every tactic reads:
`covered` (cumulative covered leaves — pool & gradient read this),
`corpus` (inputs that each grew coverage — mutation perturbs these),
and `history` (per-input deltas — drives the report's growth curve and
per-leaf first-hit). `Gen.delay` re-reads it every draw, so guided
tactics see it grow; `random` ignores it.

---

## 5. The pieces

Directory layout mirrors the extension points — root holds the engine,
each subpackage is one thing you would extend:

```
pbt/            engine + core types     Pbt · Feedback · Coverage · Report
pbt/gen/        add an input type       Generatable · ConstantPool
pbt/analysis/   add a guard/construct   BranchTree · Predicate · Parser
pbt/strategy/   add a tactic/strategy   Tactic · Strategy
```

---

## 6. Coverage — leaf-only branch coverage

[`Parser`](../engine/src/main/scala/pbt/analysis/Parser.scala) walks a
method into a [`BranchTree`](../engine/src/main/scala/pbt/analysis/BranchTree.scala):
decision points become `Branch`es (one `Arm` per outcome), arm
terminals become `Leaf`s. **A leaf is the unit of coverage** — one
distinct path through the body — so the metric is *leaf-only branch
coverage*, not scoverage's raw statement coverage (which would also
count intermediate decision statements). A non-branchy method reports
`0/0` and stays out of the comparison.

Two rules keep the graph honest:

- A leaf stores its **source span**. [`Coverage`](../engine/src/main/scala/pbt/Coverage.scala)
  marks it covered when a fired scoverage offset lands **inside that
  span** — matching by *file + span containment*, never by scoverage's
  method attribution (unreliable around nested `def`s). Spans are
  method-local, so methods sharing a file scope correctly on their own.
- **Nested `def`/`object`/`class` are opaque** — separate scopes the
  walker doesn't expand; only the call shows, as a leaf.

`Coverage` tails scoverage's append-only measurement files (reads only
newly-appended ids each call), so it is O(new ids) per input.

---

## 7. What makes a tactic coverage-guided

Each `Arm` records what its guard told us — and these are the only two
things the gradient and pool need beyond live coverage:

- a numeric [`Predicate.Cond`](../engine/src/main/scala/pbt/analysis/Predicate.scala)
  when expressible; `BranchTree.leafPaths` turns these into a per-leaf
  guard conjunction;
- the **literals it mentions**, on the *satisfying* side only (the
  `else` of `n == 42` needs `n ≠ 42`, which no literal helps);
  `BranchTree.leafLiterals` gives each leaf the pool it needs.

The **gradient** reads the live coverage, finds the nearest uncovered
leaf, and hill-climbs its **branch distance** (Korel/EvoSuite — e.g.
`n == 700014` ⇒ `|n − 700014|`, raw so large gaps still slope), keeping
the closest input and mutating it. It is autonomous — the target comes
from the source and the coverage, not from the user (cf. targeted PBT,
Löscher & Sagonas). Distance is defined only for universal type-level
comparisons (numeric); strings and structure are the other tactics'
job, or the open frontier.

The **pool** injects, each draw, the union of literals that
still-uncovered leaves carry; as leaves are covered, their literals
retire. So literal injection is itself coverage-driven.

The **mutation** tactic perturbs the corpus of coverage-growing inputs
with the type's `mutate` (AFL/FuzzChick-style, multi-scale numeric
steps, shared with the gradient's climb).

---

## 8. Running the experiment

scoverage's `Invoker` accumulates hits per-JVM with no notion of a
session, so the harness runs **one forked JVM per (strategy, seed)**
(`fork := true`):

```
sbt "engine/runMain app.Main random 1"
sbt "engine/runMain app.Main coverage-guided 1"
```

Each invocation runs every benchmark against that one strategy and
writes one report per method. The Makefile sweeps `STRATEGIES × SEEDS`.

---

## 9. Output

`Pbt.check` returns a `Report`; the harness writes one `coverage.json`
per cell at
`engine/reports/statistics/<category>/<method>/<strategy>/seed=<NN>/`:

```
{ "method", "sourceFile", "strategy", "totalInputs",
  "growthCurve":  [cumulative covered leaves after each input],
  "branchTree":   nested tree; each leaf carries firstHitInput: int | null }
```

The engine emits only this raw measurement; all charts and statistics
are produced downstream by `make analyze`
([`engine/reports/scripts/compare.py`](../engine/reports/scripts/compare.py)):
per-cell `coverage.svg` (the tree, leaves coloured by coverage), and
under `_summary/`: per-bench / suite / overall coverage bars,
`blindspot.svg` (% of random's blind spot each strategy recovers),
`time_to_coverage.svg` (coverage vs input budget), `per_seed.csv`, and
`significance.csv` (Vargha–Delaney Â₁₂ effect size + Mann–Whitney U
p-value vs random — the Arcuri–Briand pair for randomized algorithms).

The split is deliberate: the engine produces the *measurement*, Python
the *presentation*. Either side can be rewritten without the other.

---

## 10. Extension points

| You want to add… | Touch |
|------------------|-------|
| A new input type | A `Generatable[A]` via `Generatable.instance` (built-ins in `pbt/gen`; SUT types in `app.Generators`, see `Tree`) |
| A new branchy construct | One case in `Parser.visit` (+ a `BranchTree` node only if its shape is genuinely new) |
| A guard the gradient can't express yet | One case in `Parser.condOf`/`exprOf` + the matching `Predicate` case |
| A new tactic | Implement `Tactic`, add a `Tactic.Kind`, wire it in `Tactic.of` |
| A new strategy | One subset of the tactics in `Strategy.all` (+ the name in the Makefile's `STRATEGIES`) |
| A new output format | A new writer alongside `Report` |

---

*Diagrams are generated by the Python scripts under
[`docs/scripts/`](scripts/); run `make diagrams` to regenerate them.*
