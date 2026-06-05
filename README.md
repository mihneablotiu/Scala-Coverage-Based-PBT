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

`random` is *literally* `Prop.forAll(arbitrary)` — stock ScalaCheck. Every other
strategy runs the same loop plus a set of **coverage-guided tactics**, each
reading the live coverage:

- **pool** — inject the literals still-uncovered branches need;
- **mutation** — perturb a corpus of coverage-increasing seeds.

A `Strategy` is a name plus a list of independent tactics. `random` has no
tactics and therefore draws exactly like ScalaCheck. `pool` injects mined
literals, `mutation` perturbs coverage-growing seeds, and `pool-mutation`
composes both. The benchmark catalogue separates these cases into
`Calibration`, `MagicLiterals`, `MutationTargets`, `MixedTargets`, and
`NumericSearch`.

## Quick start

```bash
make all       # fmt + clean + diagrams + build + run + analyze
make build     # compile every subproject
make run       # run every (strategy, seed) pair in its own forked JVM
make analyze   # render charts/trees + effect-size/significance stats from the JSON outputs
make help      # show every target
```

Requires `sbt`, `python3` (`matplotlib`), and `graphviz`
(`brew install graphviz` on macOS).

Reports land under
`engine/reports/statistics/<category>/<method>/<strategy>/seed=<NN>/`.

## Documentation

- [`docs/overview.md`](docs/overview.md) — non-technical introduction
  (why we do this, what the prototype does, what comes out).
- [`docs/architecture.md`](docs/architecture.md) — technical companion:
  the tactic model, the per-input loop, how the branch tree maps to
  coverage, the directory layout, and extension points.
