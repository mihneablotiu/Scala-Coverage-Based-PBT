# Architecture

Technical companion to [`overview.md`](overview.md). The overview
explains *what* the project does in plain terms; this document
explains *how* it's built.

---

## 1. What is in the codebase

A single sbt build with two subprojects:

| Subproject | Role                                                                                        |
|------------|---------------------------------------------------------------------------------------------|
| `sut`      | System under test — benchmark methods grouped by problem kind. Compiled with scoverage instrumentation. |
| `engine`   | Framework — domain types, ports, adapters, the use case, the `app.Main` composition root.   |

Almost all interesting code lives in `engine/`. The SUT is a
catalogue of small methods, one file per *kind of problem* random
PBT faces (`Saturated`, `MagicConstants`, `NarrowRanges`,
`Relational`, `StructuralInvariants`, `DeepConditionals`,
`StagedValidity`) plus a `Tree` data type. `app.Main` is the only
file that imports concrete adapter classes; it is also the entry
point (a plain `def main(args: Array[String])`). One invocation
runs every benchmark against one (strategy, seed) pair from the
CLI args.

The driven ports and their adapters:

| Port                       | Adapter                              | What it does                                        |
|----------------------------|--------------------------------------|-----------------------------------------------------|
| `BranchTreeBuilder`        | `ScalametaBranchTreeBuilder`         | Parses source to a `BranchTree` via Scalameta       |
| `SourceCoverageReader`     | `ScoverageSourceCoverageReader`      | Reads fired statement offsets per source file       |
| `CoverageReportWriter`     | `FileSystemCoverageReportWriter`     | Writes one `coverage.json` per cell                 |

The single driving port `TestRunner` has one adapter:
`FileSystemTestRunner`. It delegates to the use-case
`TestRunnerHandler`, which is written entirely in terms of the
three driven ports above.

Strategies are not behind a port — they live on the `Strategy[A]`
sealed trait directly (§4).

---

## 2. Ports and adapters in plain terms

A **port** is a contract: "I need something that does X." An
**adapter** is a concrete implementation of that contract.

The discipline: the core of the application is written in terms of
ports only. It does not know that the AST shape comes from
Scalameta, or that the report goes to disk. It only knows there is
a tree builder it can ask to parse a method, a coverage reader it
can ask for fired statements, a writer it can hand a finished
report to.

Adapters fit a port without leaking concrete details into it. The
scoverage adapter exposes `coverage(sourceFile): Set[Pos]`,
not `getScoverageStatement`. Generic word, generic shape.

The result is a small core in the middle (the "hexagon"),
surrounded by adapters that plug it into the real world. Swap an
adapter, leave the core alone.

![Hexagonal architecture](images/hexagon.png)

---

## 3. The three driven ports

### `BranchTreeBuilder`

Takes a Scala source file and a method name, returns a
[`ParsedMethod`](../engine/src/main/scala/domain/ParsedMethod.scala)
— the method's branch tree of branchy expressions (`if`, `match`,
`while`, `for`-comprehensions, partial functions) **plus the
literals mined from its body** (`ints`, `longs`, `doubles`, `strings` — what the
`*-pool` strategies inject). Two rules keep the tree honest:

- *Branches split, sequences don't.* A decision point becomes a
  `Branch` with one arm per outcome; everything non-branchy is a
  flat `Leaf`. An `if` with no `else` drops its synthetic else arm.
  A `for`-comprehension desugars to `withFilter`/`map` calls that
  scoverage records as one statement, so its body becomes one leaf
  over the whole comprehension; branches *inside* a fold/`for` body
  still surface.
- *Nested `def`s are opaque.* They are separate methods, so the
  walker does not descend into them; only the *call* shows up, as a
  leaf in the enclosing body.

Each leaf records its source span (`pos`..`end`). The builder also
translates each branch guard into a small numeric condition language
([`Predicate`](../engine/src/main/scala/domain/Predicate.scala)) when
it can (comparisons, arithmetic, `&&`/`||`/`!`, boolean params,
literal `case`s) and records the method's parameter count — both feed
the `coverage-guided` branch-distance objective (§4). Guards it can't
express (string/structure/`forall`) are left empty; that leaf just
gets no gradient. Backed by Scalameta today; could be the Scala 3
compiler without touching the use case.

### `SourceCoverageReader`

One operation: `coverage(sourceFile)` returns the offsets of every
statement scoverage has fired so far in that file. The use case
marks a leaf covered when one of those offsets falls **inside the
leaf's span** — matching by *file + span containment*, never by
scoverage's method attribution (which is unreliable around nested
`def`s, where it mis-files the enclosing method's own statements).
Leaf spans are method-local, so this scopes correctly on its own.
The reader is called once per iteration so the per-input delta can
be computed.

### `CoverageReportWriter`

Takes a finished `SessionReport[A]` and persists it. Today's
adapter writes one `coverage.json` per (method, strategy) — see
§6. All graphics, tables, and cross-strategy aggregations are
produced downstream by the Python scripts under
[`engine/reports/scripts/`](../engine/reports/scripts/). A future
adapter could push to a database, an HTML dashboard, or
Prometheus — one new adapter, zero changes to the use case.

---

## 4. Strategies — sealed-trait dispatch, not a port

Each `Strategy[A]` produces the next input from the running feedback:

```
val gen: Gen[A] = strategy.gen(feedback)
```

Every strategy holds a
[`Generatable[A]`](../engine/src/main/scala/domain/Generatable.scala)
— the one type class that bundles a uniform draw (`arbitrary`), a
mutation (`mutate`), and a pool-aware draw (`pooled`), folding into
one what used to be three separate type classes. So the whole engine
carries a single context bound, `[A: Generatable]`.

There are **three feedback channels** layered over the `random`
baseline, each attacking a different *kind* of hard branch:

- **pool** (`random-pool`) — `pooled` mixes in the literals mined
  from the method's source (the `42`, the `"admin"`). Hits magic
  *literal* branches.
- **mutation** (`mutation-guided`) — 50/50 fresh draw vs. perturbing
  a corpus seed (an input that previously covered a new leaf). Hits
  *edge values* (`NaN`/`∞`, boundaries) the multi-scale `mutate`
  emits.
- **coverage-guided** — an autonomous **branch-distance** search
  (§4a). Hits *numeric targets and relations between inputs*.

The first two are four small `Strategy` case classes (`random`,
`random-pool`, `mutation-guided`, `mutation-guided-pool`) listed in
`Strategy.all`. The third **wraps** any of those four, giving eight
strategies in total, up to `coverage-guided-mutation-guided-pool`
(all three channels). The channels are **complementary** — a method
like `compareInts` (a relation) is reachable only by the gradient,
`accessLevel` (a magic string) only by the pool, `magnitude` (`NaN`)
only by mutation — so the all-three composite covers the most.

![Feedback channels are complementary](images/mechanisms.png)

### 4a. The coverage-guided branch-distance objective

`coverage-guided` is the one strategy that gets a genuinely new
signal. Random only ever learns *hit or miss*; this strategy learns
*how close a miss was*, and steers downhill — classic search-based
testing (Korel; EvoSuite), in the spirit of targeted PBT (Löscher &
Sagonas), but **autonomous** rather than user-driven:

1. The builder has already turned each branch guard into a
   [`Predicate.Cond`](../engine/src/main/scala/domain/Predicate.scala)
   where possible (§3), and `BranchTree.leafPaths` gives each leaf
   the conjunction of guards on its path.
2. Each draw, it reads the **live coverage** (`feedback.coveredBranches`)
   to see which leaves are still uncovered, binds the input's numeric
   parameters, and computes the **branch distance** to the *nearest*
   uncovered leaf (e.g. for `n == 700014`, distance `|n − 700014|`;
   distances are raw, not normalised, so large gaps still slope).
3. It hill-climbs: keep the lowest-distance input, mutate it
   (multi-scale numeric steps), accept if closer, restart
   occasionally. Once a leaf is covered it drops out of the live set
   and the search re-targets the next.
4. When no guard on the path is numerically expressible (strings,
   structure), there's no gradient, so it **defers to its base
   strategy** — which is exactly what makes the composites work:
   `coverage-guided-pool` falls back to literal injection, etc.

`CoverageGuided` is a plain `final class` (not a case class): it
holds per-session mutable search state (the current best input), so
unlike the stateless base strategies it isn't a value. One instance
is built per session, so the state isn't shared. Adding a strategy
is one entry in `Strategy.all` (a new channel base) plus the matching
name in the Makefile's `STRATEGIES`.

The `TestRunner` port's `property: A => Boolean` signature is honest
about what a client passes in. Today the engine always returns `true`
to ScalaCheck so coverage measurement keeps going regardless of the
predicate's outcome, but a future client wiring a real assertion is
free to depend on the boolean being honoured.

---

## 5. The system in motion

### 5a. One JVM per strategy

scoverage's `Invoker` accumulates statement hits within a JVM and
has no notion of a "session", so running two strategies inside the
same JVM would leak the first strategy's coverage into the
second's view. The composition root sidesteps this by running one
strategy per invocation:

```
sbt "engine/runMain app.Main random 1"
sbt "engine/runMain app.Main coverage-guided 1"
```

Each invocation forks a fresh JVM (`fork := true` in `build.sbt`)
and runs every benchmark against that one strategy. The Makefile's
`run` target loops over `STRATEGIES`.

Per-method scoping inside one JVM needs no method filtering: the
reader returns the whole file's fired offsets, and each method's
leaf spans only match offsets within its own body (other methods
in the same file live in disjoint source ranges).

### 5b. The per-iteration feedback cycle

![Per-iteration feedback cycle](images/loop.png)

The loop is ScalaCheck's own driver — `Test.check` running a
`Prop.forAllNoShrink` whose generator is
`Gen.delay(strategy.gen(feedback))` — not a hand-rolled fold. That
is the point: the `random` strategy is then *literally*
`Prop.forAll(arbitrary)`, the exact thing a ScalaCheck user writes,
so the baseline the thesis measures against is the real tool, not
an approximation of it. Coverage observation rides along as a side
effect in the property body, which always returns `true` (the
engine measures coverage regardless of the predicate's verdict).

`SessionFeedback` is the running accumulator grown by `append`:
`history: Vector[Set[Pos]]` (each input's newly-covered-branch
delta — what drives the growth curve and per-leaf first-hit),
`seeds: Vector[A]` (the inputs whose iteration newly covered a
branch — the corpus the mutation strategies perturb), and the
cumulative `coveredBranches: Set[Pos]`, kept as a field so `append`
is O(delta) rather than re-derived each call. `growthCurve` is
derived. `Gen.delay` re-reads the live feedback on every draw, so
the mutation strategies see the corpus grow; `Random` / `RandomPool`
ignore it.

---

## 6. The data the framework produces

`SessionReport[A]` is pure data — six fields, zero methods, no I/O
types:

```
methodName, sourceName   ── identity (plain strings, not a Path)
branchTree               ── static AST analysis (Option[BranchTree])
strategy                 ── which strategy ran this session (name string)
pool                     ── literals the strategy was given (empty for non-pool)
feedback                 ── loop history
```

Every derived number you might want — covered / total counts,
saturation index, per-branch first-hit, smoothed growth curve — is
computed downstream by the Python scripts under
[`engine/reports/scripts/`](../engine/reports/scripts/). The engine
emits the raw observation once; analysis lives in Python so it can
be re-run, restyled, and extended without touching the engine.

A *leaf* of `BranchTree` is the canonical "branch" for coverage:
each leaf is one distinct path through the method body, and the
decision points (rectangles in the rendered tree) are not counted
— they're intermediate nodes, not paths. The handler marks a leaf
covered when a fired scoverage offset lands inside its span (§3),
feeding that set into `SessionFeedback`, so the loop never sees
non-leaf hits. The unit reported throughout the framework is
therefore **leaf-only branch coverage** — distinct paths through
the method body — *not* scoverage's raw statement-level coverage,
which would also count the intermediate decision-point statements.

### What ends up on disk

One file per cell, written by the engine:

```
engine/reports/statistics/<category>/<methodName>/<strategy>/seed=<NN>/coverage.json
```

JSON shape:

```
{
  "method":       string,
  "sourceFile":   string,
  "strategy":     string,
  "totalInputs":  int,
  "growthCurve":  [int, …],   cumulative leaf-count per iteration
  "constantPool": { "ints": [...], "longs": [...], "doubles": [...], "strings": [...] },
  "branchTree":   nested      every leaf carries firstHitInput: int | null
}
```

`make analyze` (or `python3 engine/reports/scripts/compare.py`)
walks those JSONs and writes, alongside each cell:

```
coverage.dot       branch tree (Graphviz)
coverage.svg       rendered tree, leaves coloured by coverage
```

…plus, under `engine/reports/statistics/_summary/`:

```
by_bench/<bench>.svg   horizontal bars per (method, strategy); each bar's
                       label carries peak coverage %, the input that
                       reached it, and (for non-random strategies) the
                       speed comparison against random
suite.svg              horizontal bars per (bench, strategy)
overall.svg            one bar per strategy across the whole suite
blindspot.svg          % of random's blind spot each strategy recovers
time_to_coverage.svg   coverage vs input budget (log x) — efficiency
per_seed.csv           one row per (bench, method, strategy, seed)
```

The split is deliberate: the engine produces the *measurement*,
the Python scripts produce the *presentation*. Either side can be
rewritten without touching the other.

---

## 7. Extension points

| You want to add…                              | Touch                                                                          |
|-----------------------------------------------|--------------------------------------------------------------------------------|
| A new Scala branchy construct                 | One case in `ScalametaBranchTreeBuilder.visit` (+ a new `BranchTree` node only if its shape is genuinely different) |
| A new input type                              | Provide a `Generatable[A]` via `Generatable.instance`. Built-ins (`Int`, `Long`, `Boolean`, `String`, `Double`, `Option[A]`, `List[A]`, `Map[K,V]`, tuples) live in `Generatable`; SUT-specific types are wired in `app.Generators` (see `Tree`) |
| A new input-picking strategy                  | One new `Strategy[A]` with its own `gen` + one entry in `Strategy.all` (and the name in the Makefile's `STRATEGIES`). `coverage-guided` wraps each base automatically |
| A guard the gradient can't yet express        | One case in `ScalametaBranchTreeBuilder.condOf`/`exprOf` (e.g. a string or structural distance) + the matching case in `Predicate` |
| A new coverage source                         | New `SourceCoverageReader` adapter                                              |
| A new output format (HTML, Prometheus, …)     | New `CoverageReportWriter` adapter                                              |

---

*Diagrams are generated by the Python scripts under
[`docs/scripts/`](scripts/). Run `make diagrams` to regenerate
them.*
