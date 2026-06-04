# Scala-Coverage-Based-PBT

A research prototype that asks a single question: *does feeding
branch-coverage feedback back into ScalaCheck's input picker beat
uniform random sampling on small branchy methods?*

Two sbt subprojects:

| Subproject | Role                                                                                |
|------------|-------------------------------------------------------------------------------------|
| `sut`      | System under test — small branchy benchmark methods, compiled with scoverage instrumentation. Currently a placeholder `Saturated` group; the full suite is being rebuilt. |
| `engine`   | The `pbt` framework (one uniform coverage-guided tactic model) plus the `app` experiment harness. |

You give a method, a property, and a strategy — one call:

```scala
val pbt = new Pbt(Paths.get("sut"))
pbt.check[Int](source, "sign", Strategy.poolMutation) { n => Saturated.sign(n); true }
```

`random` is *literally* `Prop.forAll(arbitrary)` — stock ScalaCheck. Every other
strategy runs the same loop plus a set of **coverage-guided tactics**, each
reading the live coverage:

- **pool** — inject the literals still-uncovered branches need;
- **mutation** — perturb a corpus of coverage-increasing seeds.

A `Strategy` is just two on/off switches (*pool?* *mutation?*), so the four
strategies are the four combinations — `random` is both off,
`pool-mutation` both on. The two are **complementary**: the pool hits
magic literals/strings, mutation climbs *structured* targets (a sorted
prefix, a tall tree) — so the `pool-mutation` strategy wins.

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
