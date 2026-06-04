## Common commands for the coverage-based PBT POC. `make help` lists every target.

.DEFAULT_GOAL := help

REPORTS_DIR    := engine/reports/statistics
SCOV_DATA_DIR  := sut/target/scala-2.13/scoverage-data
SBT            ?= sbt
PY             ?= python3

# One JVM per (strategy, seed) keeps scoverage's process-global Invoker from leaking coverage
# between runs. STRATEGIES must stay aligned with Strategy.names; SEEDS is swept for K-seed
# variability so downstream charts can report median + IQR instead of a single noisy point.
STRATEGIES     := random random-pool mutation-guided mutation-guided-pool coverage-guided coverage-guided-pool coverage-guided-mutation-guided coverage-guided-mutation-guided-pool
# 30 seeds: the conventional minimum for assessing randomized algorithms with
# Vargha–Delaney Â₁₂ + Mann–Whitney U (Arcuri & Briand 2014), surfaced in significance.csv.
SEEDS          := $(shell seq 1 30)

# `make probe` exploration knobs — sweep one or more seeds at a chosen per-seed budget. Defaults: a single
# seed at a high budget; for several seeds with fewer inputs each, e.g.:
#   make probe PROBE_SEEDS="1 2 3 4 5" PROBE_INPUTS=10000
PROBE_SEEDS    ?= $(shell seq 1 10)
PROBE_INPUTS   ?= 10000
PROBE_OUT      := engine/reports/probe.log   # gitignored capture; summarised at the end

.PHONY: help all build run analyze clean clean-reports fmt diagrams probe

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make all             fmt + clean + diagrams + build + run + analyze"
	@echo "  make build           Compile all subprojects"
	@echo "  make run             Run each (strategy, seed) pair in its own forked JVM"
	@echo "  make probe           Probe every strategy on each method (app.Probe); prints coverage, leaves reports untouched"
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

probe: ## Probe every strategy × PROBE_SEEDS on each method (app.Probe) at PROBE_INPUTS; prints coverage, never writes $(REPORTS_DIR).
	@# Probe reads coverage but doesn't persist reports, so it can't clobber a prior `make run` sweep. Fresh SUT
	@# instrumentation (sut/clean; sut/compile) keeps the static statement IDs aligned with the bytecode.
	$(SBT) -no-colors -batch "sut/clean; sut/compile"
	@: > $(PROBE_OUT)
	@for s in $(STRATEGIES); do \
	  for k in $(PROBE_SEEDS); do \
	    printf "  probing %-38s seed=%s (inputs=$(PROBE_INPUTS)) ...\n" "$$s" "$$k"; \
	    $(SBT) -no-colors -batch "engine/runMain app.Probe $$s $$k $(PROBE_INPUTS)" >> $(PROBE_OUT) 2>&1 || { tail -20 $(PROBE_OUT); exit 1; }; \
	  done; \
	done
	@$(PY) engine/reports/scripts/probe_summary.py $(PROBE_OUT)

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
