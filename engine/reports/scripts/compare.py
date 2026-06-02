#!/usr/bin/env python3
"""Render coverage artefacts from engine output.

Reads every ``engine/reports/statistics/<bench>/<method>/<strategy>/seed=<NN>/coverage.json``
written by the Scala engine across the K-seed sweep (`SEEDS` in the Makefile) and produces:

  per cell (next to the json, one tree per seed)
    coverage.dot   — branch tree, leaves coloured by coverage
    coverage.svg   — `dot -Tsvg` rendering of the above

  cross-strategy (under engine/reports/statistics/_summary/)
    by_bench/<bench>.svg — horizontal grouped bars per (method, strategy);
                           bar height is the median coverage % across seeds
                           and labels carry the median %, the [min–max]
                           range, and (for non-random strategies) the
                           median paired-speedup factor versus random
    suite.svg            — horizontal bars per (bench, strategy), median +
                           [min–max] across seeds
    overall.svg          — horizontal bars per strategy, same aggregation
    blindspot.svg        — per-bench + suite-wide percentage of random's
                           blind spot (the leaves random failed to reach)
                           that each non-random strategy covered. Median +
                           [min–max] across the K per-seed fills. Isolates
                           "branches only coverage feedback can find" from
                           "easy guards everyone hits", which the raw
                           coverage % conflates.
    per_seed.csv         — one row per (bench, method, strategy, seed) with
                           covered/total/pct/saturation_input. Lets you do
                           ad-hoc analysis without re-loading the JSONs.

Run: ``python3 engine/reports/scripts/compare.py``  (or ``make analyze``)
"""
from __future__ import annotations

import csv
import json
import re
import statistics
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import matplotlib.pyplot as plt

# ── Paths ────────────────────────────────────────────────────────────────

STATS_ROOT = Path(__file__).resolve().parent.parent / "statistics"
SUMMARY_DIR = STATS_ROOT / "_summary"

# ── Palette (editorial duo) ──────────────────────────────────────────────

STRATEGY_COLORS = {
    "random": "#2E5C8A",                   # cobalt blue
    "random-pool": "#1F8A70",              # deep teal
    "mutation-guided": "#E67E22",          # vibrant orange
    "mutation-guided-pool": "#8E44AD",     # plum
    "mutation-guided-adaptive": "#C0392B", # brick red
}
FALLBACK_COLOR = "#7F8C8D"
BORDER = "#2C3E50"
GRID = "#E5E8EC"
TEXT = "#2C3E50"
BG = "#FFFFFF"

# DOT tree colours: green = covered / red = not reached, in the same key.
NODE_COV, NODE_MIS = "#A9DFBF", "#E6B0AA"
LEAF_COV, LEAF_MIS = "#27AE60", "#C0392B"
COV_TXT, MIS_TXT = "#196F3D", "#922B21"

RANDOM = "random"

# Canonical strategy order, simplest → most complex. Mirrors `Strategy.names` and `STRATEGIES`.
STRATEGY_ORDER = ["random", "random-pool", "mutation-guided", "mutation-guided-pool", "mutation-guided-adaptive"]

# Number of seed-runs found; set in main(), used in chart titles.
SEED_COUNT = 0

# ── Data model ───────────────────────────────────────────────────────────


@dataclass(frozen=True)
class Cell:
    bench: str
    method: str
    strategy: str
    seed: int
    total_inputs: int
    growth_curve: list
    branch_tree: Optional[dict]
    path: Path


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


def load_cells() -> list:
    cells = []
    for jp in sorted(STATS_ROOT.glob("*/*/*/*/coverage.json")):
        if "_summary" in jp.parts:
            continue
        m = _SEED_DIR_RE.match(jp.parts[-2])
        if not m:
            continue
        data = json.loads(jp.read_text())
        cells.append(
            Cell(
                bench=jp.parts[-5],
                method=jp.parts[-4],
                strategy=jp.parts[-3],
                seed=int(m.group(1)),
                total_inputs=data["totalInputs"],
                growth_curve=data["growthCurve"],
                branch_tree=data["branchTree"],
                path=jp.parent,
            )
        )
    return cells


def leaves(tree: Optional[dict]) -> list:
    if tree is None:
        return []
    if tree["kind"] == "leaf":
        return [tree]
    if tree["kind"] == "branch":
        return [leaf for arm in tree["arms"] for leaf in leaves(arm["body"])]
    if tree["kind"] == "sequence":
        return [leaf for child in tree["children"] for leaf in leaves(child)]
    return []


def is_reached(tree: dict) -> bool:
    if tree["kind"] == "leaf":
        return tree["firstHitInput"] is not None
    if tree["kind"] == "branch":
        return any(is_reached(arm["body"]) for arm in tree["arms"])
    if tree["kind"] == "sequence":
        return any(is_reached(child) for child in tree["children"])
    return False


def stats(cell: Cell) -> Stats:
    ls = leaves(cell.branch_tree)
    total = len(ls)
    covered = sum(1 for leaf in ls if leaf["firstHitInput"] is not None)
    saturation = None
    if cell.growth_curve:
        final = cell.growth_curve[-1]
        if final > 0:
            saturation = cell.growth_curve.index(final)
    return Stats(covered=covered, total=total, saturation=saturation)


# ── matplotlib styling helper ───────────────────────────────────────────


def style_axes(ax) -> None:
    for spine in ax.spines.values():
        spine.set_color(BORDER)
        spine.set_linewidth(0.7)
    ax.tick_params(colors=TEXT, labelsize=9)
    ax.xaxis.label.set_color(TEXT)
    ax.yaxis.label.set_color(TEXT)


# ── Per-cell DOT + SVG ───────────────────────────────────────────────────


def html_escape(s: str) -> str:
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def render_dot(cell: Cell) -> str:
    nodes, edges = [], []
    counter = [0]

    def fresh() -> str:
        counter[0] += 1
        return f"n{counter[0]}"

    def edge(parent: str, child: str, label: str) -> str:
        attr = f' [label="{html_escape(label)}"]' if label else ""
        return f"  {parent} -> {child}{attr};"

    def walk(tree: dict, parent: str, label: str) -> None:
        if tree["kind"] == "leaf":
            nid = fresh()
            covered = tree["firstHitInput"] is not None
            fill = LEAF_COV if covered else LEAF_MIS
            txt = COV_TXT if covered else MIS_TXT
            status = "covered" if covered else "not reached"
            lbl = (
                f'<font face="Courier" point-size="12"><b>{html_escape(tree["text"])}</b></font>'
                f'<br/><font point-size="10"><font color="{txt}"><b>{status}</b></font></font>'
            )
            nodes.append(
                f'  {nid} [shape=diamond, style="filled", fillcolor="{fill}", '
                f'penwidth=1.5, label=<{lbl}>];'
            )
            edges.append(edge(parent, nid, label))
        elif tree["kind"] == "branch":
            nid = fresh()
            reached = is_reached(tree)
            fill = NODE_COV if reached else NODE_MIS
            txt = COV_TXT if reached else MIS_TXT
            status = "reached" if reached else "not reached"
            lbl = (
                f'<font point-size="11"><b>{tree["branchKind"]}</b></font>'
                f'<br/><font face="Courier" point-size="11"><b>{html_escape(tree["label"])}</b></font>'
                f'<br/><font point-size="10"><font color="{txt}"><b>{status}</b></font></font>'
            )
            nodes.append(
                f'  {nid} [shape=box, style="rounded,filled", fillcolor="{fill}", '
                f'penwidth=1, label=<{lbl}>];'
            )
            edges.append(edge(parent, nid, label))
            for arm in tree["arms"]:
                walk(arm["body"], nid, arm["label"])
        elif tree["kind"] == "sequence":
            for child in tree["children"]:
                walk(child, parent, label)

    s = stats(cell)
    title = (
        f'<font point-size="14"><b>{html_escape(cell.method)}</b></font>'
        f'<br/><font point-size="11">branches: <b>{s.pct:.0f}%</b> '
        f'({s.covered}/{s.total}) — {cell.strategy}</font>'
    )
    nodes.append(
        f'  root [shape=box, style="rounded,filled", fillcolor="white", '
        f'penwidth=2, label=<{title}>];'
    )
    if cell.branch_tree is not None:
        walk(cell.branch_tree, "root", "")

    return (
        f'digraph "{cell.method}" {{\n'
        f"  rankdir=TB;\n"
        f'  bgcolor="white";\n'
        f'  node [fontname="Helvetica", fontsize=11, color="{BORDER}"];\n'
        f'  edge [color="{BORDER}", arrowsize=0.7, fontname="Helvetica", fontsize=9];\n\n'
        + "\n".join(nodes) + "\n\n"
        + "\n".join(edges) + "\n"
        "}\n"
    )


def write_cell_dot_svg(cell: Cell) -> None:
    dot_path = cell.path / "coverage.dot"
    svg_path = cell.path / "coverage.svg"
    dot_path.write_text(render_dot(cell))
    subprocess.run(["dot", "-Tsvg", str(dot_path), "-o", str(svg_path)], check=True)


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


# ── Per-bench grouped bars ──────────────────────────────────────────────
#
# Each bar's height is the median peak coverage % across the K seed-runs;
# labels carry the median % and the [min–max] full-range tail from the K
# runs. Non-random bars also show the median paired-speedup factor versus
# random — paired meaning "for each seed, how many fewer inputs did this
# strategy need to reach random's peak coverage on that same seed",
# aggregated by median across the K pairings.


def paired_speedup(cells: list, random_cells: list) -> Optional[float]:
    """Median paired speedup across seeds. Pairs cells and random_cells by ``seed``; for each pair
    where the strategy reached random's peak coverage, computes ``random_saturation / matched_at``.
    Returns the median of those ratios, or ``None`` if no seed yielded a defined ratio."""
    rand_by_seed = {c.seed: c for c in random_cells}
    speedups: list = []
    for c in cells:
        rc = rand_by_seed.get(c.seed)
        if rc is None:
            continue
        rs = stats(rc)
        cs = stats(c)
        if rs.covered == 0 or cs.covered < rs.covered:
            continue
        try:
            matched_at = c.growth_curve.index(rs.covered)
        except ValueError:
            continue
        rand_sat = rs.saturation or 0
        if matched_at <= 0 or rand_sat <= 0:
            continue
        speedups.append(rand_sat / matched_at)
    return statistics.median(speedups) if speedups else None


def bench_bar_label(cells: list, random_cells: list) -> str:
    """Median + [min–max] coverage label for K seed-runs of one (method, strategy) cell;
    appends the paired median speedup when applicable."""
    if not cells:
        return ""
    pcts = [stats(c).pct for c in cells]
    spread = SeedSpread.of(pcts)
    if spread.median == 0.0 and spread.hi == 0.0:
        return ""
    base = f"{spread.median:.0f}% [{spread.lo:.0f}–{spread.hi:.0f}]"
    strategy = cells[0].strategy
    if strategy == RANDOM or not random_cells:
        return base
    speedup = paired_speedup(cells, random_cells)
    if speedup is None:
        return f"{base}, < random"
    return f"{base}, {speedup:.1f}× vs random"


def write_bench_bars(bench: str, cells_by_method: dict, out_path: Path) -> None:
    methods = sorted(cells_by_method.keys())
    all_strats = {c.strategy for cs in cells_by_method.values() for c in cs}
    strategies = ordered_strategies(all_strats)
    pcts_by_strategy: dict = {}
    labels_by_strategy: dict = {}
    for strategy in strategies:
        pcts, labels = [], []
        for m in methods:
            method_cells = cells_by_method[m]
            random_cells = [c for c in method_cells if c.strategy == RANDOM]
            cells = [c for c in method_cells if c.strategy == strategy]
            spread = SeedSpread.of([stats(c).pct for c in cells])
            pcts.append(spread.median)
            labels.append(bench_bar_label(cells, random_cells))
        pcts_by_strategy[strategy] = pcts
        labels_by_strategy[strategy] = labels
    _horizontal_grouped_bars(
        methods,
        pcts_by_strategy,
        strategies,
        title=f"{bench} — coverage per method per strategy (median, [min–max] across K={SEED_COUNT} seeds)",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        label_fontsize=8,
        row_height_in=0.42,
        fig_width_in=14.0,
        label_pad_pct=90.0,
    )


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
        title=f"Per-bench aggregate coverage per strategy (median, [min–max] across K={SEED_COUNT} seeds)",
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
        f"Suite-wide aggregate coverage per strategy (median, [min–max] across K={SEED_COUNT} seeds)",
        color=TEXT, fontsize=12, pad=10,
    )
    ax.grid(True, axis="x", alpha=0.6, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


# ── Blind-spot fill (random-relative metric) ────────────────────────────
#
# Raw coverage % conflates two effects: easy "trivial" early-exit arms that
# any strategy hits in the first dozen inputs, and hard branches that random
# may never reach. The blind-spot fill metric separates them: it counts only
# leaves random missed, then asks how many of those each non-random strategy
# covered. Useful as the headline number for "is coverage feedback worth it?"


def blindspot_pairs(cells_by_method: dict, strategy: str) -> list:
    """For each leaf where ``random`` missed in any method of ``cells_by_method``,
    return ``True`` if ``strategy`` covered that leaf, ``False`` otherwise.

    Both cells share the same parsed branch tree, so leaves in document order
    line up one-to-one — paired by ``zip``. Cells must share a single ``seed``;
    blind-spot fill is computed per seed and then aggregated across seeds.
    """
    pairs = []
    for method_cells in cells_by_method.values():
        rc = next((c for c in method_cells if c.strategy == RANDOM), None)
        sc = next((c for c in method_cells if c.strategy == strategy), None)
        if rc is None or sc is None:
            continue
        for rl, sl in zip(leaves(rc.branch_tree), leaves(sc.branch_tree)):
            if rl["firstHitInput"] is None:
                pairs.append(sl["firstHitInput"] is not None)
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
        title=f"Blind-spot fill — % of leaves random missed that the strategy covered (median, [min–max] across K={SEED_COUNT} seeds)",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        row_height_in=0.85,
        label_pad_pct=45.0,
        xlabel="blind spot covered (%)",
    )


# ── Entry point ─────────────────────────────────────────────────────────


def ensure_dot_available() -> None:
    try:
        subprocess.run(["dot", "-V"], capture_output=True, check=True)
    except (subprocess.CalledProcessError, FileNotFoundError):
        sys.exit(
            "graphviz 'dot' not found — install with 'brew install graphviz' "
            "(or your package manager's equivalent)"
        )


def write_per_seed_csv(cells: list, out_path: Path) -> None:
    """One row per (bench, method, strategy, seed). Strategy ordering follows the canonical
    simplest→complex order so the CSV reads in the same direction as every chart."""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    rank = {s: i for i, s in enumerate(STRATEGY_ORDER)}
    with out_path.open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["bench", "method", "strategy", "seed", "covered", "total", "pct", "saturation_input"])
        for c in sorted(cells, key=lambda x: (x.bench, x.method, rank.get(x.strategy, len(STRATEGY_ORDER)), x.strategy, x.seed)):
            s = stats(c)
            w.writerow([
                c.bench,
                c.method,
                c.strategy,
                c.seed,
                s.covered,
                s.total,
                f"{s.pct:.2f}",
                "" if s.saturation is None else s.saturation,
            ])


# ── Coverage vs input budget (efficiency curve) ──────────────────────────
#
# Final coverage % hides *how fast* a strategy gets there. This curve plots
# suite-wide coverage against the number of inputs spent per method (log x),
# so "guidance reaches the same coverage in fewer inputs" becomes visible.

_BUDGETS = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000]


def _suite_coverage_at(cells: list, budget: int) -> Optional[float]:
    """Σ covered-leaves-after-`budget`-inputs / Σ total-leaves over a seed's cells. `growth_curve[i]`
    is the cumulative covered-leaf count after input i+1, so budget t reads index min(t, len) - 1."""
    cov = tot = 0
    for c in cells:
        total = len(leaves(c.branch_tree))
        if total == 0 or not c.growth_curve:
            continue
        cov += c.growth_curve[min(budget, len(c.growth_curve)) - 1]
        tot += total
    return (100.0 * cov / tot) if tot else None


def time_to_coverage(cells: list) -> dict:
    """{strategy: [median suite-coverage % at each budget in _BUDGETS]}, median across seeds."""
    by_strat_seed: dict = {}
    for c in cells:
        by_strat_seed.setdefault(c.strategy, {}).setdefault(c.seed, []).append(c)
    curves: dict = {}
    for strat, seeds in by_strat_seed.items():
        curve = []
        for b in _BUDGETS:
            vals = [v for sc in seeds.values() for v in [_suite_coverage_at(sc, b)] if v is not None]
            curve.append(statistics.median(vals) if vals else 0.0)
        curves[strat] = curve
    return curves


def write_time_to_coverage(curves: dict, out_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(10.0, 6.0), facecolor=BG)
    for strat in ordered_strategies(curves.keys()):
        ax.plot(_BUDGETS, curves[strat], marker="o", markersize=3, linewidth=1.8, color=color_for(strat), label=strat)
    ax.set_xscale("log")
    ax.set_xlim(1, 10000)
    ax.set_ylim(0, 100)
    ax.set_xlabel("inputs per method (log scale)")
    ax.set_ylabel("suite coverage (%)")
    ax.set_title(f"Coverage vs input budget (suite-wide median across K={SEED_COUNT} seeds)", color=TEXT, fontsize=12, pad=10)
    ax.legend(loc="lower right", framealpha=0.95, edgecolor=BORDER)
    ax.grid(True, which="both", axis="both", alpha=0.5, color=GRID, linewidth=0.6)
    style_axes(ax)
    fig.tight_layout()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out_path, facecolor=fig.get_facecolor())
    plt.close(fig)


def main() -> None:
    if not STATS_ROOT.exists():
        sys.exit(f"No data at {STATS_ROOT}. Run `make run` first.")
    cells = load_cells()
    if not cells:
        sys.exit(
            f"No <bench>/<method>/<strategy>/seed=<NN>/coverage.json files under {STATS_ROOT}. "
            "Run `make run` first."
        )

    ensure_dot_available()
    global SEED_COUNT
    SEED_COUNT = len({c.seed for c in cells})
    print(f"loaded {len(cells)} cells across {SEED_COUNT} seeds")

    for cell in cells:
        write_cell_dot_svg(cell)
    print(f"  wrote per-cell coverage.dot/svg for {len(cells)} cells")

    by_bench: dict = {}
    for c in cells:
        by_bench.setdefault(c.bench, []).append(c)

    for bench, cs in by_bench.items():
        cells_by_method: dict = {}
        for c in cs:
            cells_by_method.setdefault(c.method, []).append(c)
        out = SUMMARY_DIR / "by_bench" / f"{bench}.svg"
        write_bench_bars(bench, cells_by_method, out)
    print(f"  wrote {len(by_bench)} per-bench bar charts")

    write_suite_bars(by_bench, SUMMARY_DIR / "suite.svg")
    write_overall_bars(cells, SUMMARY_DIR / "overall.svg")
    write_blindspot_bars(by_bench, SUMMARY_DIR / "blindspot.svg")
    write_per_seed_csv(cells, SUMMARY_DIR / "per_seed.csv")
    curves = time_to_coverage(cells)
    write_time_to_coverage(curves, SUMMARY_DIR / "time_to_coverage.svg")
    print(f"  wrote suite.svg, overall.svg, blindspot.svg, time_to_coverage.svg, per_seed.csv in {SUMMARY_DIR}")

    # Suite-wide blind-spot summary: of the leaves random never reached, how many did
    # each non-random strategy cover? Honest companion to the raw-coverage bars above.
    # Aggregated across the K seed-runs as median + [min–max] range.
    suite_cells = list(cells)
    non_random = [s for s in ordered_strategies({c.strategy for c in cells}) if s != RANDOM]
    if non_random:
        print()
        print("Blind-spot fill (suite-wide, median across seeds):")
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

    # Efficiency: suite-wide coverage reached at a few input budgets (median across seeds).
    print()
    print("Coverage vs input budget (suite-wide median %):")
    checkpoints = [10, 100, 1000, 10000]
    cols = {b: _BUDGETS.index(b) for b in checkpoints}
    print(f"  {'strategy':<24}" + "".join(f"{('@' + str(b)):>10}" for b in checkpoints))
    for strat in ordered_strategies(curves.keys()):
        print(f"  {strat:<24}" + "".join(f"{curves[strat][cols[b]]:>9.1f}%" for b in checkpoints))


if __name__ == "__main__":
    main()
