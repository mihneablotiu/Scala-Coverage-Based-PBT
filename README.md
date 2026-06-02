# Scala-Coverage-Based-PBT

A research prototype that asks a single question: *does feeding
branch-coverage feedback back into ScalaCheck's input picker beat
uniform random sampling on small branchy methods?*

Two sbt subprojects:

| Subproject | Role                                                                                |
|------------|-------------------------------------------------------------------------------------|
| `sut`      | System under test — benchmark methods grouped by the *kind of problem* random PBT faces, compiled with scoverage instrumentation. |
| `engine`   | Hexagonal core — domain types, ports, adapters, the use case, the `app.Main` composition root. |

The four strategies, simplest → most complex: `random` (stock
ScalaCheck), `random-pool` (inject literals mined from the source),
`mutation-guided` (perturb a corpus of coverage-increasing seeds),
`mutation-guided-pool` (both). `random` is *literally* a plain
`Prop.forAll(arbitrary)` — the honest baseline.

## Quick start

```bash
make all       # fmt + clean + diagrams + build + run + analyze
make build     # compile every subproject
make run       # run every (strategy, seed) pair in its own forked JVM
make analyze   # render charts/trees from the JSON outputs
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
  hexagonal layout, the driven ports, the per-iteration feedback
  cycle, how the branch tree maps to coverage, extension points.
