## Common commands for the coverage-based PBT POC. `make help` lists every target.

.DEFAULT_GOAL := help

REPORTS_DIR    := engine/reports/statistics
SCOV_DATA_DIR  := sut/target/scala-2.13/scoverage-data
SCOV_HTML_DIR  := sut/target/scala-2.13/scoverage-report
SCOV_SNAP_DIR  := $(REPORTS_DIR)/_scoverage
SBT            ?= sbt
PY             ?= python3
INPUTS         ?= 10000

STRATEGIES     ?= random pool mutation pool-mutation
SEEDS          ?= $(shell seq 1 30)

.PHONY: help all build run analyze clean clean-reports fmt diagrams

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make all             fmt + clean + diagrams + build + run + analyze"
	@echo "  make build           Compile all subprojects"
	@echo "  make run             Run each (strategy, seed) pair in its own forked JVM"
	@echo "                       Optional: make run SEEDS=\"1 2\" INPUTS=1000"
	@echo "                       Saves scoverage HTML under $(SCOV_SNAP_DIR)/<strategy>/seed=<NN>/"
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
	    seed_dir=$$(printf "seed=%02d" $$k); \
	    echo "── $$s seed=$$k ──"; \
	    $(SBT) -no-colors -batch "engine/runMain app.Main $$s $$k $(INPUTS)" || exit 1; \
	    $(SBT) -no-colors -batch "sut/coverageReport" || exit 1; \
	    rm -rf "$(SCOV_SNAP_DIR)/$$s/$$seed_dir"; \
	    mkdir -p "$(SCOV_SNAP_DIR)/$$s/$$seed_dir"; \
	    cp -R "$(SCOV_HTML_DIR)/." "$(SCOV_SNAP_DIR)/$$s/$$seed_dir/"; \
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
