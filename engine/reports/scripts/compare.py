#!/usr/bin/env python3
"""Render coverage artefacts from engine output.

Reads every ``engine/reports/statistics/<bench>/<method>/<strategy>/coverage.json``
written by the Scala engine and produces:

  per cell (next to the json)
    coverage.dot   — branch tree, leaves coloured by coverage
    coverage.svg   — `dot -Tsvg` rendering of the above

  cross-strategy (under engine/reports/statistics/_summary/)
    by_bench/<bench>.svg — horizontal grouped bars per (method, strategy);
                           bar labels carry the peak coverage %, the input
                           that reached it, and (for non-random strategies)
                           the speed comparison against random
    suite.svg            — horizontal bars per (bench, strategy)
    overall.svg          — horizontal bars per strategy
    blindspot.svg        — per-bench + suite-wide percentage of random's
                           blind spot (the leaves random failed to reach)
                           that each non-random strategy covered. Isolates
                           "branches only coverage feedback can find" from
                           "easy guards everyone hits", which the raw
                           coverage % conflates.

Run: ``python3 engine/reports/scripts/compare.py``  (or ``make analyze``)
"""
from __future__ import annotations

import json
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
    "random": "#2E5C8A",          # cobalt blue
    "mutation-guided": "#E67E22", # vibrant orange
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

# ── Data model ───────────────────────────────────────────────────────────


@dataclass(frozen=True)
class Cell:
    bench: str
    method: str
    strategy: str
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


def color_for(strategy: str) -> str:
    return STRATEGY_COLORS.get(strategy, FALLBACK_COLOR)


def ordered_strategies(strategies) -> list:
    """Random first (when present), then everything else alphabetically."""
    others = sorted(s for s in strategies if s != RANDOM)
    head = [RANDOM] if RANDOM in strategies else []
    return head + others


# ── Loading + tree walk ──────────────────────────────────────────────────


def load_cells() -> list:
    cells = []
    for jp in sorted(STATS_ROOT.glob("*/*/*/coverage.json")):
        if "_summary" in jp.parts:
            continue
        data = json.loads(jp.read_text())
        cells.append(
            Cell(
                bench=jp.parts[-4],
                method=jp.parts[-3],
                strategy=jp.parts[-2],
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
# Each bar carries the strategy's peak coverage % and the input that
# reached it; non-random bars additionally call out how they compare to
# random — either a speed-up factor when they matched random's coverage,
# or the % they fell short of.


def bench_bar_label(cell: Optional[Cell], random_stats: Optional[Stats]) -> str:
    if cell is None:
        return ""
    s = stats(cell)
    if s.total == 0:
        return ""
    if s.saturation is None:
        return f"{s.pct:.0f}%"
    base = f"{s.pct:.0f}% @ #{s.saturation}"
    if cell.strategy == RANDOM or random_stats is None or random_stats.covered == 0:
        return base
    if s.covered < random_stats.covered:
        return f"{base}, < random ({random_stats.pct:.0f}%)"
    matched_x = cell.growth_curve.index(random_stats.covered)
    rand_sat = random_stats.saturation or 0
    speedup = rand_sat / max(matched_x, 1)
    return f"{base}, reached {random_stats.pct:.0f}% at #{matched_x} ({speedup:.1f}×)"


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
            random_cell = next((c for c in method_cells if c.strategy == RANDOM), None)
            random_stats = stats(random_cell) if random_cell else None
            cell = next((c for c in method_cells if c.strategy == strategy), None)
            pcts.append(stats(cell).pct if cell else 0.0)
            labels.append(bench_bar_label(cell, random_stats))
        pcts_by_strategy[strategy] = pcts
        labels_by_strategy[strategy] = labels
    _horizontal_grouped_bars(
        methods,
        pcts_by_strategy,
        strategies,
        title=f"{bench} — coverage per method per strategy",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        label_fontsize=8,
        row_height_in=0.42,
        fig_width_in=14.0,
        label_pad_pct=80.0,
    )


# ── Suite bars (per bench × strategy) ───────────────────────────────────


def write_suite_bars(cells_by_bench: dict, out_path: Path) -> None:
    benches = sorted(cells_by_bench.keys())
    all_strats = {c.strategy for cs in cells_by_bench.values() for c in cs}
    strategies = ordered_strategies(all_strats)
    pcts_by_strategy: dict = {}
    labels_by_strategy: dict = {}
    for strategy in strategies:
        pcts = []
        for b in benches:
            scoped = [c for c in cells_by_bench[b] if c.strategy == strategy]
            total = sum(stats(c).total for c in scoped)
            covered = sum(stats(c).covered for c in scoped)
            pcts.append((100.0 * covered / total) if total > 0 else 0.0)
        pcts_by_strategy[strategy] = pcts
        labels_by_strategy[strategy] = [f"{p:.1f}%" for p in pcts]
    _horizontal_grouped_bars(
        benches,
        pcts_by_strategy,
        strategies,
        title="Per-bench aggregate coverage per strategy",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        row_height_in=0.85,
    )


# ── Overall (one bar per strategy across the suite) ─────────────────────


def write_overall_bars(cells: list, out_path: Path) -> None:
    strategies = ordered_strategies({c.strategy for c in cells})
    fig_h = max(2.8, 0.9 * len(strategies) + 1.4)
    fig, ax = plt.subplots(figsize=(8.5, fig_h), facecolor=BG)
    for i, strategy in enumerate(strategies):
        scoped = [c for c in cells if c.strategy == strategy]
        total = sum(stats(c).total for c in scoped)
        covered = sum(stats(c).covered for c in scoped)
        pct = (100.0 * covered / total) if total > 0 else 0.0
        ax.barh(
            i,
            pct,
            0.55,
            color=color_for(strategy),
            edgecolor=BORDER,
            linewidth=0.6,
        )
        ax.text(pct + 1.5, i, f"{pct:.1f}%", va="center", fontsize=11, color=TEXT)
    ax.set_yticks(list(range(len(strategies))))
    ax.set_yticklabels(strategies, color=TEXT, fontsize=11)
    ax.invert_yaxis()
    ax.set_xlim(0, 112)
    ax.set_xlabel("coverage (%)")
    ax.set_title("Suite-wide aggregate coverage per strategy", color=TEXT, fontsize=12, pad=10)
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
    line up one-to-one — paired by ``zip``.
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


def write_blindspot_bars(cells_by_bench: dict, out_path: Path) -> None:
    """Per-bench + suite-wide percentage of random's blind spot covered by each non-random strategy.

    Benches where random already saturated have no blind spot and are skipped
    from the chart (they show up only in the stdout summary).
    """
    benches = sorted(cells_by_bench.keys())
    cells_by_bench_method: dict = {}
    for b, cs in cells_by_bench.items():
        cells_by_bench_method[b] = {}
        for c in cs:
            cells_by_bench_method[b].setdefault(c.method, []).append(c)
    non_random = sorted({c.strategy for cs in cells_by_bench.values() for c in cs} - {RANDOM})
    if not non_random:
        return

    bench_pairs = {(b, s): blindspot_pairs(cells_by_bench_method[b], s)
                   for b in benches for s in non_random}
    benches_with_gap = [b for b in benches if any(bench_pairs[(b, s)] for s in non_random)]
    if not benches_with_gap:
        return

    categories = benches_with_gap + ["— Suite —"]
    pcts_by_strategy: dict = {}
    labels_by_strategy: dict = {}
    for s in non_random:
        pcts, labels = [], []
        suite = []
        for b in benches_with_gap:
            pairs = bench_pairs[(b, s)]
            suite.extend(pairs)
            f, t = sum(pairs), len(pairs)
            pcts.append(100.0 * f / t if t else 0.0)
            labels.append(f"{f}/{t} leaves")
        f, t = sum(suite), len(suite)
        pcts.append(100.0 * f / t if t else 0.0)
        labels.append(f"{f}/{t} leaves")
        pcts_by_strategy[s] = pcts
        labels_by_strategy[s] = labels

    _horizontal_grouped_bars(
        categories,
        pcts_by_strategy,
        non_random,
        title="Blind-spot fill — % of leaves random missed that the strategy covered",
        out_path=out_path,
        labels_by_strategy=labels_by_strategy,
        row_height_in=0.85,
        label_pad_pct=40.0,
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


def main() -> None:
    if not STATS_ROOT.exists():
        sys.exit(f"No data at {STATS_ROOT}. Run `make run` first.")
    cells = load_cells()
    if not cells:
        sys.exit(f"No coverage.json files under {STATS_ROOT}.")

    ensure_dot_available()
    print(f"loaded {len(cells)} cells")

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
    print(f"  wrote suite.svg, overall.svg, blindspot.svg in {SUMMARY_DIR}")

    # Suite-wide blind-spot summary: of the leaves random never reached, how many did
    # each non-random strategy cover? Honest companion to the raw-coverage bars above.
    flat_by_method: dict = {}
    for c in cells:
        flat_by_method.setdefault((c.bench, c.method), []).append(c)
    non_random = sorted({c.strategy for c in cells} - {RANDOM})
    if non_random:
        print()
        print("Blind-spot fill (suite-wide):")
        for strat in non_random:
            pairs = blindspot_pairs(flat_by_method, strat)
            if pairs:
                f, t = sum(pairs), len(pairs)
                print(f"  {strat:<18s} filled {f}/{t} = {100.0 * f / t:.1f}% of random's blind spot")
            else:
                print(f"  {strat:<18s} no blind spot to fill (random saturated everywhere)")


if __name__ == "__main__":
    main()
