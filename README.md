# Scala-Coverage-Based-PBT

A research prototype that asks a single question: *does feeding
branch-coverage feedback back into ScalaCheck's input picker beat
uniform random sampling on small branchy methods?*

Two sbt subprojects:

| Subproject | Role                                                                                |
|------------|-------------------------------------------------------------------------------------|
| `sut`      | System under test — benchmark methods grouped by the *kind of problem* random PBT faces, compiled with scoverage instrumentation. |
| `engine`   | Hexagonal core — domain types, ports, adapters, the use case, the `app.Main` composition root. |

Over the `random` baseline (`random` is *literally* `Prop.forAll(arbitrary)`,
the honest baseline) the engine layers three **feedback channels**:

- **pool** — inject literals mined from the method's own source (`random-pool`);
- **mutation** — perturb a corpus of coverage-increasing seeds (`mutation-guided`);
- **coverage-guided** — *autonomously* derive a branch-distance objective from the
  source and the live coverage, and hill-climb toward the nearest still-uncovered
  branch (no hand-written objective).

They compose, so the full strategy list is `random`, `random-pool`,
`mutation-guided`, `mutation-guided-pool`, and `coverage-guided[-pool]
[-mutation-guided]` — up to `coverage-guided-mutation-guided-pool`, which
carries all three. The channels are **complementary**: the pool hits magic
literals/strings, mutation hits float edge values, and the gradient hits numeric
targets and *relations between inputs* — so the all-three strategy wins.

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
  hexagonal layout, the driven ports, the per-iteration feedback
  cycle, how the branch tree maps to coverage, extension points.
