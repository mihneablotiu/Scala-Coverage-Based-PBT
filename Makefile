## Common commands for the coverage-based PBT POC.
## Run `make help` (or just `make`) to see the available targets.

.DEFAULT_GOAL := help

# ── Locations ──────────────────────────────────────────────────────────
REPORTS_DIR    := engine/reports
SCOV_DATA_DIR  := sut/target/scala-2.13/scoverage-data
SBT            ?= sbt

# ── Strategies ─────────────────────────────────────────────────────────
# One JVM per strategy: scoverage's Invoker accumulates statement hits within a JVM and has no
# notion of a session, so two strategies sharing a JVM would see each other's coverage. Forking
# the engine once per strategy (via `engine/runMain` with `fork := true` in build.sbt) isolates
# them. Keep this list aligned with `Strategy.all` in `engine/src/main/scala/domain/Strategy.scala`.
STRATEGIES     := random mutation-guided feedback-bias-guided

.PHONY: help all build run svg compare clean clean-reports fmt diagrams

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make all             ABSOLUTELY everything: fmt + clean + diagrams + build + run + svg + compare"
	@echo "  make build           Compile all subprojects"
	@echo "  make run             Run every strategy in $(STRATEGIES), each in its own forked JVM"
	@echo "  make svg             Render every coverage.dot under $(REPORTS_DIR) to SVG"
	@echo "  make compare         Cross-strategy comparison charts + markdown table under $(REPORTS_DIR)/_summary/"
	@echo "  make diagrams        Regenerate the architecture diagrams under docs/images/"
	@echo "  make clean-reports   Remove $(REPORTS_DIR) and stale scoverage measurements"
	@echo "  make clean           sbt clean + clean-reports"
	@echo "  make fmt             scalafmt on every Scala source"

# ── One-shot pipeline ──────────────────────────────────────────────────
# `all` is the single command for a fully reproducible from-scratch run: format the source,
# wipe every build/report artefact, regenerate the architecture diagrams, recompile, run each
# strategy in its own forked JVM, render every coverage DOT to SVG, and emit the cross-strategy
# comparison charts + markdown table. Run serially (do not pass `-j`); the steps have implicit
# ordering dependencies (fmt before compile, clean before build, run before svg/compare, …).
all: fmt clean diagrams build run svg compare ## ABSOLUTELY everything: fmt + clean + diagrams + build + run + svg + compare.

# ── Scala build ────────────────────────────────────────────────────────
build: ## Compile every subproject.
	$(SBT) -no-colors -batch compile

run: clean-reports ## Run each strategy in its own forked JVM (app.Main per strategy).
	@# Force a fresh SUT instrumentation. `sbt compile` regenerates `scoverage.coverage` on
	@# every invocation but only recompiles classes when sources change, so the static
	@# statement IDs in the coverage file drift apart from the IDs baked into the bytecode
	@# whenever only engine sources have moved. A clean SUT rebuild keeps both in sync.
	$(SBT) -no-colors -batch "sut/clean; sut/compile"
	@for s in $(STRATEGIES); do \
	  echo "── $$s ──"; \
	  $(SBT) -no-colors -batch "engine/runMain app.Main $$s" || exit 1; \
	done

# ── Rendering ──────────────────────────────────────────────────────────
svg: ## Render every coverage.dot file produced by `make run` to SVG.
	@command -v dot >/dev/null 2>&1 || { \
	  echo "graphviz 'dot' not found — install with 'brew install graphviz'"; exit 1; }
	@find $(REPORTS_DIR) -type f -name 'coverage.dot' | while read -r f; do \
	  out="$${f%.dot}.svg"; \
	  dot -Tsvg "$$f" -o "$$out" && echo "wrote $$out"; \
	done

# ── Cross-strategy comparison ──────────────────────────────────────────
compare: ## Cross-strategy comparison charts + markdown table from existing reports.
	@python3 docs/diagrams/compare.py

# ── Cleaning ───────────────────────────────────────────────────────────
clean-reports: ## Remove $(REPORTS_DIR) and stale scoverage measurement files.
	@rm -rf $(REPORTS_DIR)
	@find $(SCOV_DATA_DIR) -name 'scoverage.measurements.*' -delete 2>/dev/null || true

clean: clean-reports ## sbt clean + clean-reports.
	$(SBT) -no-colors -batch clean

# ── Formatting ─────────────────────────────────────────────────────────
fmt: ## Apply scalafmt to all Scala sources.
	$(SBT) -no-colors -batch scalafmtAll

# ── Architecture diagrams ──────────────────────────────────────────────
diagrams: ## Regenerate every PNG/SVG under docs/images/ from docs/diagrams/*.py.
	@python3 docs/diagrams/generate.py
