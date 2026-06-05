# Architecture

Technical companion to [`overview.md`](overview.md). The overview
explains *what* the project does in plain terms; this explains *how*
it is built.

---

## 1. Two subprojects

| Subproject | Role |
|------------|------|
| `sut`    | System under test — benchmark categories compiled with scoverage instrumentation. |
| `engine` | The `pbt` framework and an `app` experiment harness with concrete generators. |

The SUT catalogue has five categories: `Calibration`, `MagicLiterals`,
`MutationTargets`, `MixedTargets`, and `NumericSearch`. Each category has
five methods and isolates a different coverage story.

---

## 2. Using the framework — one call

```scala
val pbt    = new Pbt(Paths.get("sut"))
val report = pbt.check[Int](source, "magicInt", Strategy.pool) { n =>
  MagicLiterals.magicInt(n); true
}
```

You give a **method** (its source file + name), a **property** over
its input type, and a **strategy**. `check` generates inputs,
runs the property on each while measuring coverage, and returns a
[`Report`](../engine/src/main/scala/pbt/Report.scala). The input type
needs a [`Generatable`](../engine/src/main/scala/pbt/gen/Generatable.scala)
in scope; the supported instances (`Int`, `List`, `Option`,
`benchmark.data.Tree`, and tuples) live together in
[`app.Generators`](../engine/src/main/scala/app/Generators.scala).

`random` is *literally* `Prop.forAll(arbitrary)` — stock ScalaCheck.
Every other strategy runs the identical loop and only adds
coverage-guided tactics, so the baseline is the real tool, not an
approximation.

---

## 3. Strategy = random plus independent tactics

A [`Strategy`](../engine/src/main/scala/pbt/strategy/Strategy.scala) is
a name plus the tactics it enables:

```scala
final case class Strategy(name: String, tactics: List[Tactic])
```

Each draw is a plain random draw, plus — when the strategy enables them
and the live coverage says they can still help — a **pooled** draw and a
**mutated** draw mixed in (random keeps a fixed minority share, so it
stays a healthy escape hatch and seeds the corpus):

- **pool** — inject the literals that still-uncovered branches need
  (`Generatable.pooled`), while some leaf is still uncovered.
- **mutation** — perturb a corpus seed, an input that grew coverage
  (`Generatable.mutate`).

So the **four strategies are the combinations of the implemented
tactics** — `random` has no tactics, `pool-mutation` has both. The
whole mixing logic lives in
[`Pbt.check`](../engine/src/main/scala/pbt/Pbt.scala).

![Feedback tactics are complementary](images/mechanisms.png)

The tactics are **complementary** — a branch behind a magic integer is
reached only by the pool, a branch behind a *structured* input (a
sorted list, staged tuple, or tree shape) only by mutation — so the `pool-mutation`
composite covers the most.

---

## 4. The loop

![Both tactics combined](images/combined.png)

Each tactic combined with the random baseline is drawn on its own —
[pool](images/pool.png), [mutation](images/mutation.png) — and
[`sources.png`](images/sources.png) contrasts stock ScalaCheck's single
random source with the guided sources.

The driver is ScalaCheck's own (`Test.check` over a
`Prop.forAllNoShrink` whose generator is `Gen.delay(...)` re-read each
draw) — not a hand-rolled fold, which is what lets `random` *be* plain
ScalaCheck. Per input:

1. **draw** — a random draw, plus pooled/mutated draws the strategy enables;
2. **run** the property (guarded, so a thrown exception still counts);
3. **read** the offsets scoverage fired in the method's file;
4. **mark** the leaves those offsets land inside as covered;
5. **record** into [`Feedback`](../engine/src/main/scala/pbt/strategy/Feedback.scala).

`Feedback` is the single running signal:
`covered` (cumulative covered leaves — pooling stops once nothing is
left to cover), `corpus` (inputs that each grew coverage — mutation
perturbs these), and `history` (per-input deltas — gives each leaf its
first-hit input index). `Gen.delay` re-reads it every draw, so the
guided draws see it grow; `random` ignores it.

---

## 5. The pieces

Directory layout mirrors the extension points — root holds the engine,
each subpackage is one thing you would extend:

```
pbt/            engine + core types     Pbt · Coverage · Report
pbt/gen/        the generator interface Generatable · ConstantPool
pbt/analysis/   add a construct         Analysis (BranchTree + Parser)
pbt/strategy/   feedback state + presets Feedback · Strategy · Tactic
app/            harness + all the       Main · Generators
                concrete generators
sut/benchmark/  SUT methods + data      Calibration · ... · data.Tree
```

Each subpackage is one cohesive file, so "where does X live" has one
answer.

---

## 6. Coverage — leaf-only branch coverage

[`Parser`](../engine/src/main/scala/pbt/analysis/Analysis.scala) walks a
method into a [`BranchTree`](../engine/src/main/scala/pbt/analysis/Analysis.scala):
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

`Coverage` reads scoverage's append-only measurement files whole on
every call — they are tiny, so the simple re-read beats tracking
incremental state. Stale measurements are wiped when it is constructed,
so each run starts clean.

---

## 7. What makes a tactic coverage-guided

Both tactics steer off the *live* coverage — they propose only while
there is something left to gain, so a strategy is genuinely
feedback-driven, not a fixed bias:

The **pool** mines every `Int` literal in the method body into
a [`ConstantPool`](../engine/src/main/scala/pbt/gen/ConstantPool.scala)
(the AFL *dictionary* idea — splice useful constants from the program into
inputs — adapted to value-level draws; over-approximating is cheap, an
unused literal is just a wasted draw). Each draw it injects one of those
literals — but only while some leaf is still uncovered; once every leaf
is hit there is nothing a literal can unlock, so it stands down. That
is what makes literal injection coverage-driven.

The **mutation** tactic perturbs the corpus of coverage-growing inputs
with the type's `mutate` (AFL/FuzzChick-style edits and "interesting"
edge values). It favours the *most recent* grower — the input on the
coverage frontier — so each retained seed is the springboard that
ratchets into nearby structured inputs (sort a list, grow a tree), with a
slice of draws on older seeds for diversity.

Neither needs anything from the parser beyond the `BranchTree`'s leaves
(for the pool to know when to retire) and the mined literals — the
guard *text* is kept only for the report's labels.

---

## 8. Running the experiment

scoverage's `Invoker` accumulates hits per-JVM with no notion of a
session, so the harness runs **one forked JVM per (strategy, seed)**
(`fork := true`):

```
sbt "engine/runMain app.Main random 1"
sbt "engine/runMain app.Main pool-mutation 1"
```

Each invocation runs every benchmark against that one strategy and
writes one report per method. The Makefile sweeps `STRATEGIES × SEEDS`.

---

## 9. Output

`Pbt.check` returns a `Report`; the harness writes one `coverage.json`
per cell at
`engine/reports/statistics/<category>/<method>/<strategy>/seed=<NN>/`:

```
{ "method", "sourceFile", "strategy", "totalInputs", "elapsedMillis",
  "pool":       { "ints": [...] },
  "branchTree": nested tree; each leaf carries firstHitInput: int | null }
```

The growth curve is **not** stored: each leaf's `firstHitInput` already
encodes when coverage grew, so the cumulative curve is reconstructed
downstream — the file stays O(leaves), not O(inputs).

The engine emits only this raw measurement; all charts and statistics
are produced downstream by `make analyze`
([`engine/reports/scripts/compare.py`](../engine/reports/scripts/compare.py)):
per-cell `coverage.svg` (the tree, leaves coloured by coverage), and
under `_summary/`: per-bench / suite / overall coverage bars,
`blindspot.svg` (% of random's blind spot each strategy recovers),
`time_to_coverage.svg` (coverage vs input budget, from the
reconstructed curve), `per_seed.csv`, and `significance.csv`
(Vargha–Delaney Â₁₂ effect size + Mann–Whitney U p-value vs random —
the Arcuri–Briand pair for randomized algorithms).

The split is deliberate: the engine produces the *measurement*, Python
the *presentation*. Either side can be rewritten without the other.

---

## 10. Extension points

| You want to add… | Touch |
|------------------|-------|
| A new input type | One `implicit Generatable[A]` object in `app.Generators` — its `arbitrary` / `mutate` / `pooled` (all the concrete generators live there; composites defer to their components, see `list`/`tree`) |
| A new branchy construct | One case in `Parser.visit` (+ a `BranchTree` node only if its shape is genuinely new) |
| A new tactic | One `Tactic` case plus one proposal branch in `Pbt.check` |
| A new strategy | One `Strategy` preset in `Strategy.all` (+ the name in the Makefile's `STRATEGIES`) |
| A new output format | A new writer alongside `Report` |

---

*Diagrams are generated by the Python scripts under
[`docs/scripts/`](scripts/); run `make diagrams` to regenerate them.*
