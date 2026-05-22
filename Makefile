## Common commands for the coverage-based PBT POC.
## Run `make help` (or just `make`) to see the available targets.

.DEFAULT_GOAL := help

# ── Locations ──────────────────────────────────────────────────────────
REPORTS_DIR    := engine/reports
SCOV_DATA_DIR  := sut/target/scala-2.13/scoverage-data
SBT            ?= sbt

.PHONY: help all build run svg clean clean-reports fmt

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make all             Run the whole pipeline: clean + build + run + svg"
	@echo "  make build           Compile all subprojects"
	@echo "  make run             Run the benchmark suite (clears $(REPORTS_DIR) first)"
	@echo "  make svg             Render every coverage.dot under $(REPORTS_DIR)/*/visuals/ to SVG"
	@echo "  make clean-reports   Remove $(REPORTS_DIR) and stale scoverage measurements"
	@echo "  make clean           sbt clean + clean-reports"
	@echo "  make fmt             scalafmt on every Scala source"

# ── One-shot pipeline ──────────────────────────────────────────────────
all: clean build run svg ## Wipe everything, rebuild, run the suite, render SVGs.

# ── Scala build ────────────────────────────────────────────────────────
build: ## Compile every subproject.
	$(SBT) -no-colors -batch compile

run: clean-reports ## Run the benchmark suite (app.Main in engine).
	$(SBT) -no-colors -batch "engine/run"

# ── Rendering ──────────────────────────────────────────────────────────
svg: ## Render every coverage.dot file produced by `make run` to SVG.
	@command -v dot >/dev/null 2>&1 || { \
	  echo "graphviz 'dot' not found — install with 'brew install graphviz'"; exit 1; }
	@for d in $(REPORTS_DIR)/*/visuals/; do \
	  if [ -f "$$d/coverage.dot" ]; then \
	    dot -Tsvg "$$d/coverage.dot" -o "$$d/coverage.svg" && \
	    echo "wrote $$d/coverage.svg"; \
	  fi; \
	done

# ── Cleaning ───────────────────────────────────────────────────────────
clean-reports: ## Remove $(REPORTS_DIR) and stale scoverage measurement files.
	@rm -rf $(REPORTS_DIR)
	@find $(SCOV_DATA_DIR) -name 'scoverage.measurements.*' -delete 2>/dev/null || true

clean: clean-reports ## sbt clean + clean-reports.
	$(SBT) -no-colors -batch clean

# ── Formatting ─────────────────────────────────────────────────────────
fmt: ## Apply scalafmt to all Scala sources.
	$(SBT) -no-colors -batch scalafmtAll
