## Common commands for the coverage-based PBT POC.
## Run `make help` (or just `make`) to see the available targets.

.DEFAULT_GOAL := help

# ── Locations ──────────────────────────────────────────────────────────
OUT_DIR         := runner/out
SCOV_DATA_DIR   := sut/target/scala-2.13/scoverage-data
DIAGRAM_SCRIPT  := docs/diagrams/generate.py
PYTHON          ?= python3
SBT             ?= sbt

.PHONY: help build run svg diagrams demo all clean clean-out fmt install-deps

help: ## Show this help.
	@echo "Coverage-based PBT — common commands"
	@echo
	@echo "  make build         Compile all subprojects (sbt)"
	@echo "  make run           Run the engine (fresh scoverage data, deletes runner/out)"
	@echo "  make svg           Render coverage.dot files in runner/out/ to coverage.svg"
	@echo "  make diagrams      Regenerate architecture diagrams under docs/images/"
	@echo "  make demo          run + svg in one shot"
	@echo "  make all           run + svg + diagrams (full reproducible pipeline)"
	@echo "  make clean-out     Just remove runner/out and stale scoverage measurements"
	@echo "  make clean         sbt clean + clean-out"
	@echo "  make fmt           scalafmt on every Scala source"
	@echo "  make install-deps  pip install matplotlib (needed for 'make diagrams')"

# ── Scala build ────────────────────────────────────────────────────────
build: ## Compile every subproject.
	$(SBT) -no-colors -batch compile

run: clean-out ## Run the engine for every SUT method.
	$(SBT) -no-colors -batch "runner/run"

# ── Rendering ──────────────────────────────────────────────────────────
svg: ## Render every coverage.dot file produced by `make run` to SVG.
	@command -v dot >/dev/null 2>&1 || { \
	  echo "graphviz 'dot' not found — install with 'brew install graphviz'"; exit 1; }
	@for d in $(OUT_DIR)/*/; do \
	  d=$${d%/}; \
	  if [ -f "$$d/coverage.dot" ]; then \
	    dot -Tsvg "$$d/coverage.dot" -o "$$d/coverage.svg" && \
	    echo "wrote $$d/coverage.svg"; \
	  fi; \
	done

diagrams: ## Regenerate the architecture diagrams (docs/images/).
	$(PYTHON) $(DIAGRAM_SCRIPT)

# ── Pipelines ──────────────────────────────────────────────────────────
demo: run svg ## Quick demo: run + render coverage SVGs.

all: run svg diagrams ## Everything: run + render + regenerate diagrams.

# ── Cleaning ───────────────────────────────────────────────────────────
clean-out: ## Remove runner/out and stale scoverage measurement files.
	@rm -rf $(OUT_DIR)
	@find $(SCOV_DATA_DIR) -name 'scoverage.measurements.*' -delete 2>/dev/null || true

clean: clean-out ## sbt clean + clean-out.
	$(SBT) -no-colors -batch clean

# ── Formatting & deps ─────────────────────────────────────────────────
fmt: ## Apply scalafmt to all Scala sources.
	$(SBT) -no-colors -batch scalafmtAll

install-deps: ## Install Python dependencies for diagram regeneration.
	$(PYTHON) -m pip install --user -r docs/diagrams/requirements.txt
