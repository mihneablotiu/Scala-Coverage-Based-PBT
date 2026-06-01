# Scala-Coverage-Based-PBT

A research prototype that asks a single question: *does feeding
branch-coverage feedback back into ScalaCheck's input picker beat
uniform random sampling on small branchy methods?*

Two sbt subprojects:

| Subproject | Role                                                                                |
|------------|-------------------------------------------------------------------------------------|
| `sut`      | System under test — small benchmark methods, compiled with scoverage instrumentation. |
| `engine`   | Hexagonal core — domain types, ports, adapters, the use case, the `app.Main` composition root. |

## Quick start

```bash
make all       # fmt + clean + diagrams + build + run + analyze
make build     # compile every subproject
make run       # run every (strategy, seed) pair in its own forked JVM
make analyze   # render charts/tables from the JSON outputs
make help      # show every target
```

Requires `sbt`, `python3` (`matplotlib`), and `graphviz`
(`brew install graphviz` on macOS).

Reports land under `engine/reports/statistics/<bench>/<method>/<strategy>/seed=<NN>/`.

## Documentation

- [`docs/overview.md`](docs/overview.md) — non-technical introduction
  (why we do this, what the prototype does, what comes out).
- [`docs/architecture.md`](docs/architecture.md) — technical companion:
  hexagonal layout, the three driven ports, the per-iteration feedback
  cycle, extension points.
