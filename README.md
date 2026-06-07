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
each reading the live feedback it needs:

- **pool** — draw from mined literals while branch-marked targets remain uncovered;
- **mutation** — perturb a corpus of coverage-increasing seeds;
- **targeted** — use branch-distance feedback for numeric branch goals.

A `Strategy` chooses the next generator from the current context. `random`
therefore draws exactly like ScalaCheck. `pool` draws from mined literals,
`mutation` perturbs coverage-growing seeds, `targeted` follows numeric branch
distances, and `pool-mutation` composes pool and mutation.
The benchmark catalogue separates these cases into
`Calibration`, `MagicLiterals`, `MutationTargets`, `MixedTargets`,
`NumericSearch`, and `RealWorld`: 44 methods spanning shallow calibration cases,
exact literal gates, structured list/tree targets, mixed tactic targets, numeric
cases that expose both useful offset edits and current limitations, plus practical
string/numeric algorithms.

## Quick start

```bash
make full      # clean + format + run 30 seeds × 100000 inputs + analyze
make smoke     # same pipeline with 1 seed × 200 inputs
```

Requires `sbt` and `python3` with `matplotlib`.

The retained report output is intentionally small:

- scoverage HTML snapshots for each `(strategy, seed)` under
  `engine/reports/statistics/_scoverage/`;
- statement/branch summary SVGs and throughput under
  `engine/reports/statistics/_summary/`.

Intermediate `coverage.json` files are used to build the summaries and then
discarded by the Makefile pipeline.

## Documentation

- [`docs/overview.md`](docs/overview.md) — non-technical introduction
  (why we do this, what the prototype does, what comes out).
- [`docs/architecture.md`](docs/architecture.md) — technical companion:
  the tactic model, the per-input loop, method-local statement coverage,
  the directory layout, and extension points.
