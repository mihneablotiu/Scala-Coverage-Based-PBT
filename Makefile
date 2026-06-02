## Common commands for the coverage-based PBT POC. `make help` lists every target.

.DEFAULT_GOAL := help

REPORTS_DIR    := engine/reports/statistics
SCOV_DATA_DIR  := sut/target/scala-2.13/scoverage-data
SBT            ?= sbt
PY             ?= python3

# One JVM per (strategy, seed) keeps scoverage's process-global Invoker from leaking coverage
# between runs. STRATEGIES must stay aligned with Strategy.names; SEEDS is swept for K-seed
# variability so downstream charts can report median + IQR instead of a single noisy point.
STRATEGIES     := random random-pool mutation-guided mutation-guided-pool mutation-guided-energy
SEEDS          := 1 2 3 4 5 6 7 8 9 10

.PHONY: help all build run analyze clean clean-reports fmt diagrams

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make all             fmt + clean + diagrams + build + run + analyze"
	@echo "  make build           Compile all subprojects"
	@echo "  make run             Run each (strategy, seed) pair in its own forked JVM"
	@echo "  make analyze         Build charts/tables from $(REPORTS_DIR)/*/*/*/seed=*/coverage.json"
	@echo "  make diagrams        Regenerate architecture diagrams under docs/images/"
	@echo "  make clean-reports   Remove $(REPORTS_DIR) and stale scoverage measurements"
	@echo "  make clean           sbt clean + clean-reports"
	@echo "  make fmt             scalafmt on every Scala source"

all: fmt clean diagrams build run analyze ## Format, wipe, rebuild, run, analyze.

build: ## Compile every subproject.
	$(SBT) -no-colors -batch compile

run: clean-reports ## Run each (strategy, seed) pair in its own forked JVM (app.Main per pair).
	@# Force a fresh SUT instrumentation. sbt regenerates scoverage.coverage on every invocation
	@# but only recompiles classes when sources changed; if engine sources moved while SUT didn't,
	@# the static statement IDs drift apart from the bytecode's. `sut/clean; sut/compile` resyncs.
	$(SBT) -no-colors -batch "sut/clean; sut/compile"
	@for s in $(STRATEGIES); do \
	  for k in $(SEEDS); do \
	    echo "── $$s seed=$$k ──"; \
	    $(SBT) -no-colors -batch "engine/runMain app.Main $$s $$k" || exit 1; \
	  done; \
	done

analyze: ## Render per-cell trees + cross-strategy comparison charts (requires graphviz + matplotlib).
	@command -v dot >/dev/null 2>&1 || { \
	  echo "graphviz 'dot' not found — install with 'brew install graphviz'"; exit 1; }
	$(PY) engine/reports/scripts/compare.py

clean-reports: ## Remove $(REPORTS_DIR) and stale scoverage measurement files.
	@rm -rf $(REPORTS_DIR)
	@find $(SCOV_DATA_DIR) -name 'scoverage.measurements.*' -delete 2>/dev/null || true

clean: clean-reports ## sbt clean + clean-reports.
	$(SBT) -no-colors -batch clean

fmt: ## scalafmt on every Scala source.
	$(SBT) -no-colors -batch scalafmtAll

diagrams: ## Regenerate PNGs/SVGs under docs/images/ from docs/scripts/*.py.
	@$(PY) docs/scripts/generate.py
