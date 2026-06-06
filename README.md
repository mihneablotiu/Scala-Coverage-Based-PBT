# Scala-Coverage-Based-PBT

A research prototype that asks a single question: *does feeding
branch-coverage feedback back into ScalaCheck's input picker beat
uniform random sampling on small branchy methods?*

Two sbt subprojects:

| Subproject | Role                                                                                |
|------------|-------------------------------------------------------------------------------------|
| `sut`      | System under test — benchmark categories compiled with scoverage instrumentation. |
| `engine`   | The `pbt` framework (one uniform coverage-guided tactic model) plus the `app` experiment harness. |

You give a method, a property, and a strategy — one call:

```scala
val pbt = new Pbt(Paths.get("sut"))
pbt.check[Int](source, "magicInt", Strategy.pool) { n => MagicLiterals.magicInt(n); true }
```

`random` is ScalaCheck arbitrary generation in the same no-shrink measurement
loop as every other strategy. Guided strategies add **coverage-guided tactics**,
each reading the live coverage:

- **pool** — draw from mined literals while branch-marked targets remain uncovered;
- **mutation** — perturb a corpus of coverage-increasing seeds.

A `Strategy` chooses the next generator from the current context. `random`
therefore draws exactly like ScalaCheck. `pool` draws from mined literals,
`mutation` perturbs coverage-growing seeds, and `pool-mutation` composes both.
The benchmark catalogue separates these cases into
`Calibration`, `MagicLiterals`, `MutationTargets`, `MixedTargets`,
`NumericSearch`, and `RealWorld`: 42 methods spanning shallow calibration cases, exact
literal gates, structured list/tree targets, mixed tactic targets, and
computed numeric relations, plus practical string/numeric algorithms.

## Quick start

```bash
make full      # clean + format + run 30 seeds × 100000 inputs + analyze
make smoke     # same pipeline with 1 seed × 200 inputs
```

Requires `sbt`, Graphviz `dot`, and `python3` with `matplotlib`.

Reports land under
`engine/reports/statistics/<category>/<method>/<strategy>/seed=<NN>/`,
with `coverage.json` for first-hit timing.
Both Makefile commands snapshot scoverage's own HTML report for each
`(strategy, seed)` under `engine/reports/statistics/_scoverage/`.
They also write statement and branch aggregate charts from copied scoverage XML under
`engine/reports/statistics/_summary/`; per-method source views come from the
scoverage HTML snapshots, not from custom DOT/SVG graphs.

## Documentation

- [`docs/overview.md`](docs/overview.md) — non-technical introduction
  (why we do this, what the prototype does, what comes out).
- [`docs/architecture.md`](docs/architecture.md) — technical companion:
  the tactic model, the per-input loop, method-local statement coverage,
  the directory layout, and extension points.
