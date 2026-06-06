.DEFAULT_GOAL := smoke

REPORTS_DIR   := engine/reports/statistics
SCOV_DATA_DIR := sut/target/scala-2.13/scoverage-data
SCOV_HTML_DIR := sut/target/scala-2.13/scoverage-report
SCOV_SNAP_DIR := $(REPORTS_DIR)/_scoverage
SBT           ?= sbt
PY            ?= python3
STRATEGIES    := random pool mutation pool-mutation

.PHONY: full smoke

define run_experiment
	@rm -rf $(REPORTS_DIR)
	@find $(SCOV_DATA_DIR) -name 'scoverage.measurements.*' -delete 2>/dev/null || true
	$(SBT) -no-colors -batch clean
	$(SBT) -no-colors -batch scalafmtAll
	@$(PY) docs/scripts/generate.py
	$(SBT) -no-colors -batch "sut/clean; sut/compile"
	@for s in $(STRATEGIES); do \
	  for k in $(1); do \
	    seed_dir=$$(printf "seed=%02d" $$k); \
	    echo "── $$s seed=$$k ──"; \
	    $(SBT) -no-colors -batch "engine/runMain app.Main $$s $$k $(2)" || exit 1; \
	    $(SBT) -no-colors -batch "sut/coverageReport" || exit 1; \
	    rm -rf "$(SCOV_SNAP_DIR)/$$s/$$seed_dir"; \
	    mkdir -p "$(SCOV_SNAP_DIR)/$$s/$$seed_dir"; \
	    cp -R "$(SCOV_HTML_DIR)/." "$(SCOV_SNAP_DIR)/$$s/$$seed_dir/"; \
	  done; \
	done
	$(PY) engine/reports/scripts/compare.py
endef

full:
	$(call run_experiment,$$(seq 1 30),10000)

smoke:
	$(call run_experiment,1,200)
