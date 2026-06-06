#!/usr/bin/env python3
"""Render coverage artefacts from engine output.

Reads the Scala engine's first-hit JSON logs plus copied scoverage XML reports
for every strategy/seed and produces:

  cross-strategy (under engine/reports/statistics/_summary/)
    <metric>_suite.svg            — horizontal bars per (bench, strategy)
    <metric>_overall.svg          — horizontal bars per strategy
    <metric>_blindspot.svg        — per-bench + suite-wide percentage of random's
                           blind spot (the targets random failed to reach)
                           that each non-random strategy covered. Median +
                           [min–max] across the K per-seed fills. Isolates
                           "targets only coverage feedback can find" from
                           "easy guards everyone hits", which the raw
                           coverage % conflates.

Run: ``python3 engine/reports/scripts/compare.py``  (normally via ``make full`` or ``make smoke``)
"""
from __future__ import annotations

import json
import re
import shutil
import statistics
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional
from xml.etree import ElementTree as ET

import matplotlib.pyplot as plt

# ── Paths ────────────────────────────────────────────────────────────────

STATS_ROOT = Path(__file__).resolve().parent.parent / "statistics"
SUMMARY_DIR = STATS_ROOT / "_summary"

# ── Palette (editorial duo) ──────────────────────────────────────────────

STRATEGY_COLORS = {
    "random": "#2E5C8A",          # cobalt blue
    "pool": "#1F8A70",            # deep teal
    "mutation": "#E67E22",      # vibrant orange
    "pool-mutation": "#C0392B", # brick red
}
FALLBACK_COLOR = "#7F8C8D"
BORDER = "#2C3E50"
GRID = "#E5E8EC"
TEXT = "#2C3E50"
BG = "#FFFFFF"

RANDOM = "random"

# Canonical strategy order, simplest to most complex. Mirrors `Strategy.names`.
STRATEGY_ORDER = ["random", "pool", "mutation", "pool-mutation"]

# Number of seed-runs found; set in main(), used in chart titles.
SEED_COUNT = 0

# ── Data model ───────────────────────────────────────────────────────────


@dataclass(frozen=True)
class CoverageCounts:
    statement_covered: int
    statement_total: int
    branch_covered: int
    branch_total: int


@dataclass(frozen=True)
class Cell:
    bench: str
    method: str
    strategy: str
    seed: int
    total_inputs: int
    elapsed_millis: int
    statement_curve: list
    branch_curve: list
    statements: list
    xml_targets: list
    xml_counts: CoverageCounts

    @property
    def inputs_per_sec(self) -> Optional[float]:
        return None if self.elapsed_millis <= 0 else 1000.0 * self.total_inputs / self.elapsed_millis


METRIC = "statement"


def metric_label() -> str:
    return "branch" if METRIC == "branch" else "statement"


def metric_targets(statements: list) -> list:
    return metric_targets_for(METRIC, statements)


def metric_xml_targets(targets: list) -> list:
    return [t for t in targets if t["branch"]] if METRIC == "branch" else targets


def metric_curve(cell: Cell) -> list:
    return cell.branch_curve if METRIC == "branch" else cell.statement_curve


def metric_counts(cell: Cell) -> tuple[int, int]:
    c = cell.xml_counts
    return (c.branch_covered, c.branch_total) if METRIC == "branch" else (c.statement_covered, c.statement_total)


@dataclass(frozen=True)
class Stats:
    covered: int
    total: int
    saturation: Optional[int]

    @property
    def pct(self) -> float:
        return 0.0 if self.total == 0 else 100.0 * self.covered / self.total


@dataclass(frozen=True)
class SeedSpread:
    """Five-number summary across the K seed-runs for one cell. Bars use ``median`` for height
    and ``lo``/``hi`` for the ``[min–max]`` text in the bar label. ``q1``/``q3`` are still
    computed for the stdout summary, but no longer drive any chart geometry."""
    q1: float
    median: float
    q3: float
    lo: float
    hi: float

    @classmethod
    def of(cls, values: list) -> "SeedSpread":
        if not values:
            return cls(0.0, 0.0, 0.0, 0.0, 0.0)
        if len(values) == 1:
            v = float(values[0])
            return cls(v, v, v, v, v)
        qs = statistics.quantiles(values, n=4, method="inclusive")
        return cls(qs[0], statistics.median(values), qs[2], min(values), max(values))


def color_for(strategy: str) -> str:
    return STRATEGY_COLORS.get(strategy, FALLBACK_COLOR)


def ordered_strategies(strategies) -> list:
    """Canonical simplest→complex order, with any unknown strategies tacked on alphabetically."""
    present = set(strategies)
    head = [s for s in STRATEGY_ORDER if s in present]
    tail = sorted(present - set(STRATEGY_ORDER))
    return head + tail


# ── Loading + tree walk ──────────────────────────────────────────────────


_SEED_DIR_RE = re.compile(r"^seed=(\d+)$")


def reconstruct_growth_curve(statements: list, total_inputs: int) -> list:
    """Cumulative covered-target count after each input, rebuilt from per-target ``firstHitInput``."""
    if total_inputs <= 0:
        return []
    hits = [s["firstHitInput"] for s in statements if s["firstHitInput"] is not None]
    curve = [0] * total_inputs
    for h in hits:
        if h < total_inputs:
            curve[h] += 1
    running = 0
    for i in range(total_inputs):
        running += curve[i]
        curve[i] = running
    return curve


def load_cells() -> list:
    cells = []
    xml_cache: dict = {}
    for jp in sorted(STATS_ROOT.glob("*/*/*/*/coverage.json")):
        if "_summary" in jp.parts:
            continue
        m = _SEED_DIR_RE.match(jp.parts[-2])
        if not m:
            continue
        data = json.loads(jp.read_text())
        statements = data["statements"]
        strategy = jp.parts[-3]
        seed = int(m.group(1))
        source_file = data.get("sourceFile", f"{jp.parts[-5]}.scala")
        xml_targets = xml_method_targets(xml_cache, strategy, seed, source_file, data["method"])
        cells.append(
            Cell(
                bench=jp.parts[-5],
                method=jp.parts[-4],
                strategy=strategy,
                seed=seed,
                total_inputs=data["totalInputs"],
                elapsed_millis=data.get("elapsedMillis", 0),
                statement_curve=reconstruct_growth_curve(statements, data["totalInputs"]),
                branch_curve=reconstruct_growth_curve(metric_targets_for("branch", statements), data["totalInputs"]),
                statements=statements,
                xml_targets=xml_targets,
                xml_counts=counts_from_xml_targets(xml_targets),
            )
        )
    return cells


def xml_method_targets(cache: dict, strategy: str, seed: int, source_file: str, method: str) -> list:
    key = (strategy, seed)
    if key not in cache:
        seed_dir = f"seed={seed:02d}"
        xml_path = STATS_ROOT / "_scoverage" / strategy / seed_dir / "scoverage.xml"
        cache[key] = parse_scoverage_xml(xml_path)
    return cache[key].get((source_file, method), [])


def counts_from_xml_targets(targets: list) -> CoverageCounts:
    return CoverageCounts(
        statement_covered=sum(1 for t in targets if t["covered"]),
        statement_total=len(targets),
        branch_covered=sum(1 for t in targets if t["branch"] and t["covered"]),
        branch_total=sum(1 for t in targets if t["branch"]),
    )


def parse_scoverage_xml(path: Path) -> dict:
    if not path.exists():
        return {}
    by_method: dict = {}
    root = ET.parse(path).getroot()
    for stmt in root.findall(".//statement"):
        if stmt.get("ignored") == "true":
            continue
        source_file = Path(stmt.get("source", "")).name
        method = stmt.get("method", "")
        key = (source_file, method)
        by_method.setdefault(key, []).append({
            "start": int(stmt.get("start", "0")),
            "end": int(stmt.get("end", "0")),
            "line": int(stmt.get("line", "0")),
            "branch": stmt.get("branch") == "true",
            "covered": int(stmt.get("invocation-count", "0")) > 0,
        })
    for key, targets in by_method.items():
        by_method[key] = sorted(targets, key=lambda t: (t["start"], t["end"], t["line"], t["branch"]))
    return by_method


def metric_targets_for(metric: str, statements: list) -> list:
    return [s for s in statements if s.get("branch", False)] if metric == "branch" else statements


def stats(cell: Cell) -> Stats:
    covered, total = metric_counts(cell)
    saturation = None
    curve = metric_curve(cell)
    if curve:
        final = curve[-1]
        if final > 0:
            saturation = curve.index(final)
    return Stats(covered=covered, total=total, saturation=saturation)


# ── matplotlib styling helper ───────────────────────────────────────────


def style_axes(ax) -> None:
    for spine in ax.spines.values():
        spine.set_color(BORDER)
        spine.set_linewidth(0.7)
    ax.tick_params(colors=TEXT, labelsize=9)
    ax.xaxis.label.set_color(TEXT)
    ax.yaxis.label.set_color(TEXT)


# ── Horizontal bar helper ───────────────────────────────────────────────


def _horizontal_grouped_bars(
    categories: list,
    pcts_by_strategy: dict,
    strategies: list,
    *,
    title: str,
    out_path: Path,
    labels_by_strategy: Optional[dict] = None,
    label_fontsize: int = 9,
    row_height_in: float = 0.55,
    fig_width_in: float = 10.0,
    label_pad_pct: float = 12.0,
    xlabel: str = "coverage (%)",
) -> None:
    n_cat = len(categories)
    n_strat = len(strategies)
    if n_cat == 0 or n_strat == 0:
        return
    bar_h = 0.8 / n_strat
    fig_h = max(3.5, row_height_in * n_cat * n_strat + 1.6)
    fig, ax = plt.subplots(figsize=(fig_width_in, fig_h), facecolor=BG)
    for k, strategy in enumerate(strategies):
        pcts = pcts_by_strategy[strategy]
        labels = (labels_by_strategy or {}).get(strategy) or [f"{p:.0f}%" for p in pcts]
        # Place random (k=0) ABOVE guided (k=1) within each category group:
        # in barh, smaller y = higher on screen after invert_yaxis.
        offsets = [i + k * bar_h - 0.4 + bar_h / 2 for i in range(n_cat)]
        bars = ax.barh(
            offsets,
            pcts,
            bar_h,
            color=color_for(strategy),
            edgecolor=BORDER,
            linewidth=0.5,
            label=strategy,
        )
        for bar, pct, lbl in zip(bars, pcts, labels):
            ax.text(
                pct + 1.2,
                bar.get_y() + bar.get_height() / 2,
                lbl,
                va="center",
                fontsize=label_fontsize,
                color=TEXT,
            )
    ax.set_yticks(list(range(n_cat)))
    ax.set_yticklabels(categories, color=TEXT)
    ax.invert_yaxis()
    ax.set_xlim(0, 100 + label_pad_pct)
    ax.set_xticks([0, 25, 50, 75, 100])
    ax.set_xlabel(xlabel)
    ax.set_title(title, color=TEXT, fontsize=12, pad=10)
    ax.legend(loc="lower right", framealpha=0.95, edgecolor=BORDER)
    ax.grid(True, axis="x", alpha=0.6, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


# ── Suite bars (per bench × strategy) ───────────────────────────────────


def _per_seed_bench_pcts(cells: list) -> list:
    """For a flat list of cells (one bench × one strategy, multiple methods × K seeds),
    group by seed and reduce each seed-group to one coverage % via ``Σ covered / Σ total``.
    Returns the K per-seed percentages."""
    by_seed: dict = {}
    for c in cells:
        by_seed.setdefault(c.seed, []).append(c)

    def pct(seed_cells: list) -> float:
        tot = sum(stats(c).total for c in seed_cells)
        cov = sum(stats(c).covered for c in seed_cells)
        return (100.0 * cov / tot) if tot > 0 else 0.0

    return [pct(seed_cells) for seed_cells in by_seed.values()]


def write_suite_bars(cells_by_bench: dict, out_path: Path) -> None:
    benches = sorted(cells_by_bench.keys())
    all_strats = {c.strategy for cs in cells_by_bench.values() for c in cs}
    strategies = ordered_strategies(all_strats)
    pcts_by_strategy: dict = {}
    labels_by_strategy: dict = {}
    for strategy in strategies:
        pcts, labels = [], []
        for b in benches:
            scoped = [c for c in cells_by_bench[b] if c.strategy == strategy]
            spread = SeedSpread.of(_per_seed_bench_pcts(scoped))
            pcts.append(spread.median)
            labels.append(f"{spread.median:.1f}% [{spread.lo:.1f}–{spread.hi:.1f}]")
        pcts_by_strategy[strategy] = pcts
        labels_by_strategy[strategy] = labels
    _horizontal_grouped_bars(
        benches,
        pcts_by_strategy,
        strategies,
        title=f"Per-bench aggregate {metric_label()} coverage per strategy (median, [min–max] across K={SEED_COUNT} seeds)",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        row_height_in=0.85,
        label_pad_pct=30.0,
    )


# ── Overall (one bar per strategy across the suite) ─────────────────────


def write_overall_bars(cells: list, out_path: Path) -> None:
    strategies = ordered_strategies({c.strategy for c in cells})
    fig_h = max(2.8, 0.9 * len(strategies) + 1.4)
    fig, ax = plt.subplots(figsize=(9.5, fig_h), facecolor=BG)
    for i, strategy in enumerate(strategies):
        scoped = [c for c in cells if c.strategy == strategy]
        spread = SeedSpread.of(_per_seed_bench_pcts(scoped))
        ax.barh(
            i,
            spread.median,
            0.55,
            color=color_for(strategy),
            edgecolor=BORDER,
            linewidth=0.6,
        )
        ax.text(
            spread.median + 1.5,
            i,
            f"{spread.median:.1f}% [{spread.lo:.1f}–{spread.hi:.1f}]",
            va="center",
            fontsize=11,
            color=TEXT,
        )
    ax.set_yticks(list(range(len(strategies))))
    ax.set_yticklabels(strategies, color=TEXT, fontsize=11)
    ax.invert_yaxis()
    ax.set_xlim(0, 130)
    ax.set_xlabel("coverage (%)")
    ax.set_title(
        f"Suite-wide aggregate {metric_label()} coverage per strategy (median, [min–max] across K={SEED_COUNT} seeds)",
        color=TEXT, fontsize=12, pad=10,
    )
    ax.grid(True, axis="x", alpha=0.6, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


# ── Blind-spot fill (random-relative metric) ────────────────────────────


def blindspot_pairs(cells_by_method: dict, strategy: str) -> list:
    """For each selected target where ``random`` missed in any method of ``cells_by_method``,
    return ``True`` if ``strategy`` covered that target, ``False`` otherwise.

    Both cells share the same method statement list, so targets in document order
    line up one-to-one — paired by ``zip``. Cells must share a single ``seed``;
    blind-spot fill is computed per seed and then aggregated across seeds.
    """
    pairs = []
    for method_cells in cells_by_method.values():
        rc = next((c for c in method_cells if c.strategy == RANDOM), None)
        sc = next((c for c in method_cells if c.strategy == strategy), None)
        if rc is None or sc is None:
            continue
        for rl, sl in zip(metric_xml_targets(rc.xml_targets), metric_xml_targets(sc.xml_targets)):
            if not rl["covered"]:
                pairs.append(sl["covered"])
    return pairs


def _per_seed_blindspot_fills(cells: list, strategy: str) -> list:
    """For a flat list of cells (one or more benches × multiple methods × K seeds), return one
    blind-spot-fill % per seed. Uses paired (random, strategy) cells within the same seed; seeds
    where random saturated the scope contribute nothing (excluded, not zero) so that the spread
    reflects only seeds where there was a blind spot to fill in the first place."""
    by_seed: dict = {}
    for c in cells:
        by_seed.setdefault(c.seed, []).append(c)
    fills: list = []
    for seed, seed_cells in by_seed.items():
        cells_by_method: dict = {}
        for c in seed_cells:
            cells_by_method.setdefault(c.method, []).append(c)
        pairs = blindspot_pairs(cells_by_method, strategy)
        if pairs:
            fills.append(100.0 * sum(pairs) / len(pairs))
    return fills


def write_blindspot_bars(cells_by_bench: dict, out_path: Path) -> None:
    """Per-bench + suite-wide percentage of random's blind spot covered by each non-random strategy,
    aggregated across the K seed-runs as median + [min–max]. Benches where every seed
    saturated random (no blind spot to fill in any seed) are skipped from the chart."""
    benches = sorted(cells_by_bench.keys())
    all_strats = {c.strategy for cs in cells_by_bench.values() for c in cs}
    non_random = [s for s in ordered_strategies(all_strats) if s != RANDOM]
    if not non_random:
        return

    suite_cells = [c for cs in cells_by_bench.values() for c in cs]
    bench_fills = {(b, s): _per_seed_blindspot_fills(cells_by_bench[b], s)
                   for b in benches for s in non_random}
    benches_with_gap = [b for b in benches if any(bench_fills[(b, s)] for s in non_random)]
    if not benches_with_gap:
        return

    suite_fills = {s: _per_seed_blindspot_fills(suite_cells, s) for s in non_random}
    categories = benches_with_gap + ["— Suite —"]
    pcts_by_strategy: dict = {}
    labels_by_strategy: dict = {}
    for s in non_random:
        pcts, labels = [], []
        for b in benches_with_gap:
            spread = SeedSpread.of(bench_fills[(b, s)])
            pcts.append(spread.median)
            labels.append(f"{spread.median:.0f}% [{spread.lo:.0f}–{spread.hi:.0f}]")
        spread = SeedSpread.of(suite_fills[s])
        pcts.append(spread.median)
        labels.append(f"{spread.median:.0f}% [{spread.lo:.0f}–{spread.hi:.0f}]")
        pcts_by_strategy[s] = pcts
        labels_by_strategy[s] = labels

    _horizontal_grouped_bars(
        categories,
        pcts_by_strategy,
        non_random,
        title=f"Blind-spot fill — % of {metric_label()} targets random missed that the strategy covered (median, [min–max] across K={SEED_COUNT} seeds)",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        row_height_in=0.85,
        label_pad_pct=45.0,
        xlabel="blind spot covered (%)",
    )


# ── Coverage vs input budget (efficiency curve) ──────────────────────────

_BUDGET_STEPS = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000]


def input_budgets(cells: list) -> list:
    max_inputs = max((c.total_inputs for c in cells), default=0)
    if max_inputs <= 0:
        return []
    budgets = [b for b in _BUDGET_STEPS if b <= max_inputs]
    return budgets if budgets and budgets[-1] == max_inputs else budgets + [max_inputs]


def _suite_coverage_at(cells: list, budget: int) -> Optional[float]:
    cov = tot = 0
    for c in cells:
        total = len(metric_targets(c.statements))
        curve = metric_curve(c)
        if total == 0 or not curve:
            continue
        cov += curve[min(budget, len(curve)) - 1]
        tot += total
    return (100.0 * cov / tot) if tot else None


def time_to_coverage(cells: list, budgets: list) -> dict:
    """{strategy: [median suite-coverage % at each budget]}, median across seeds."""
    by_strat_seed: dict = {}
    for c in cells:
        by_strat_seed.setdefault(c.strategy, {}).setdefault(c.seed, []).append(c)
    curves: dict = {}
    for strat, seeds in by_strat_seed.items():
        curve = []
        for b in budgets:
            vals = [v for sc in seeds.values() for v in [_suite_coverage_at(sc, b)] if v is not None]
            curve.append(statistics.median(vals) if vals else 0.0)
        curves[strat] = curve
    return curves


def write_time_to_coverage(curves: dict, budgets: list, out_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(10.0, 6.0), facecolor=BG)
    for strat in ordered_strategies(curves.keys()):
        ax.plot(budgets, curves[strat], marker="o", markersize=3, linewidth=1.8, color=color_for(strat), label=strat)
    ax.set_xscale("log")
    ax.set_xlim(1, budgets[-1] if budgets else 1)
    ax.set_ylim(0, 100)
    ax.set_xlabel("inputs per method (log scale)")
    ax.set_ylabel(f"suite {metric_label()} coverage (%)")
    ax.set_title(f"{metric_label().title()} coverage vs input budget (suite-wide median across K={SEED_COUNT} seeds)", color=TEXT, fontsize=12, pad=10)
    ax.legend(loc="lower right", framealpha=0.95, edgecolor=BORDER)
    ax.grid(True, which="both", axis="both", alpha=0.5, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


# ── Throughput (inputs/sec per strategy) ─────────────────────────────────
#
# "Do all those tactics make us slow?" Every strategy runs the same methods,
# and the dominant per-input cost — reading the scoverage measurement file —
# is shared, so the per-strategy throughput delta isolates the tactics' own
# overhead (pooled generation, mutation). Reported as the
# median inputs/sec across all (method, seed) cells, plus the slowdown vs
# random. (Each forked JVM runs the methods in the same order, so JIT-warmup
# bias is consistent across strategies and the comparison stays fair.)


def throughput(cells: list) -> dict:
    """{strategy: median inputs/sec across its (method, seed) cells}."""
    by_strat: dict = {}
    for c in cells:
        ips = c.inputs_per_sec
        if ips is not None:
            by_strat.setdefault(c.strategy, []).append(ips)
    return {s: statistics.median(v) for s, v in by_strat.items() if v}


def write_throughput_bars(ips_by_strategy: dict, out_path: Path) -> None:
    strategies = ordered_strategies(ips_by_strategy.keys())
    if not strategies:
        return
    fig_h = max(2.8, 0.9 * len(strategies) + 1.4)
    fig, ax = plt.subplots(figsize=(9.5, fig_h), facecolor=BG)
    top = max(ips_by_strategy.values()) if ips_by_strategy else 1.0
    for i, strategy in enumerate(strategies):
        ips = ips_by_strategy[strategy]
        ax.barh(i, ips, 0.55, color=color_for(strategy), edgecolor=BORDER, linewidth=0.6)
        ax.text(ips + top * 0.01, i, f"{ips:,.0f}/s", va="center", fontsize=11, color=TEXT)
    ax.set_yticks(list(range(len(strategies))))
    ax.set_yticklabels(strategies, color=TEXT, fontsize=11)
    ax.invert_yaxis()
    ax.set_xlim(0, top * 1.18)
    ax.set_xlabel("inputs / second (median across cells)")
    ax.set_title(f"Throughput per strategy (median across K={SEED_COUNT} seeds × methods)", color=TEXT, fontsize=12, pad=10)
    ax.grid(True, axis="x", alpha=0.6, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


def main() -> None:
    if not STATS_ROOT.exists():
        sys.exit(f"No data at {STATS_ROOT}. Run `make smoke` or `make full` first.")
    cells = load_cells()
    if not cells:
        sys.exit(
            f"No <bench>/<method>/<strategy>/seed=<NN>/coverage.json files under {STATS_ROOT}. "
            "Run `make smoke` or `make full` first."
        )

    global SEED_COUNT
    SEED_COUNT = len({c.seed for c in cells})
    print(f"loaded {len(cells)} cells across {SEED_COUNT} seeds")

    if SUMMARY_DIR.exists():
        shutil.rmtree(SUMMARY_DIR)

    by_bench: dict = {}
    for c in cells:
        by_bench.setdefault(c.bench, []).append(c)

    global METRIC
    for metric in ("statement", "branch"):
        METRIC = metric
        write_suite_bars(by_bench, SUMMARY_DIR / f"{metric}_suite.svg")
        write_overall_bars(cells, SUMMARY_DIR / f"{metric}_overall.svg")
        write_blindspot_bars(by_bench, SUMMARY_DIR / f"{metric}_blindspot.svg")
        budgets = input_budgets(cells)
        curves = time_to_coverage(cells, budgets)
        write_time_to_coverage(curves, budgets, SUMMARY_DIR / f"{metric}_time_to_coverage.svg")
        print(f"  wrote {metric} summary SVGs in {SUMMARY_DIR}")

        print()
        print(f"{metric_label().title()} coverage vs input budget (suite-wide median %):")
        checkpoints = [b for b in [10, 100, 1000, 10000, 100000] if b in budgets]
        if budgets and budgets[-1] not in checkpoints:
            checkpoints.append(budgets[-1])
        cols = {b: budgets.index(b) for b in checkpoints}
        print(f"  {'strategy':<24}" + "".join(f"{('@' + str(b)):>10}" for b in checkpoints))
        for strat in ordered_strategies(curves.keys()):
            print(f"  {strat:<24}" + "".join(f"{curves[strat][cols[b]]:>9.1f}%" for b in checkpoints))

        suite_cells = list(cells)
        non_random = [s for s in ordered_strategies({c.strategy for c in cells}) if s != RANDOM]
        if non_random:
            print()
            print(f"{metric_label().title()} blind-spot fill (suite-wide, median across seeds):")
            for strat in non_random:
                fills = _per_seed_blindspot_fills(suite_cells, strat)
                if fills:
                    spread = SeedSpread.of(fills)
                    print(
                        f"  {strat:<18s} median {spread.median:.1f}% "
                        f"[range {spread.lo:.1f}%–{spread.hi:.1f}%, IQR {spread.q1:.1f}%–{spread.q3:.1f}%, "
                        f"n={len(fills)} seeds]"
                    )
                else:
                    print(f"  {strat:<18s} no blind spot to fill (random saturated in every seed)")

    ips_by_strategy = throughput(cells)
    write_throughput_bars(ips_by_strategy, SUMMARY_DIR / "throughput.svg")

    if ips_by_strategy:
        rand_ips = ips_by_strategy.get(RANDOM)
        print()
        print(f"Throughput (median inputs/sec across K={SEED_COUNT} seeds × methods):")
        print(f"  {'strategy':<40}{'inputs/sec':>12}{'vs random':>16}")
        for strat in ordered_strategies(ips_by_strategy.keys()):
            ips = ips_by_strategy[strat]
            rel = f"{ips / rand_ips:.2f}× random" if rand_ips and ips > 0 and strat != RANDOM else "—"
            print(f"  {strat:<40}{ips:>12,.0f}{rel:>16}")

if __name__ == "__main__":
    main()
