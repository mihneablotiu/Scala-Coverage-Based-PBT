#!/usr/bin/env python3
"""Strategy comparison artifacts from per-strategy coverage JSON.

Reads `engine/reports/<Bench>/<method>/<strategy>/data/coverage.json` for every
strategy and writes four flavours of comparison artefacts.

**Per-method and per-bench charts** pack each strategy's headline numbers into
one bar with a single combined value label:

    `61%  (11/18)  ·  sat #481  ·  → random at #37`

The `→ random at #N` addendum appears only on a *guided* strategy's bar, and
only when the guided strategy **strictly beat** random's coverage and matched
random's covered count at an input *other* than its own plateau (when those
two numbers coincide the `sat #N` already carries that information).

**Aggregate charts** (suite-wide and overall) show coverage *only*: the
"mean saturation" of a heterogeneous set of methods is a number with no
intuitive reading — averaging "first input that plateaued" across two- and
twenty-branch methods doesn't compare like with like. The per-bench /
per-method views above are where the per-method saturation lives.

1. **Per-method** — one SVG per (Bench, method) at
   `engine/reports/<Bench>/<method>/comparison.svg`. Single horizontal panel
   with one bar per strategy.

2. **Per-bench detailed** — one SVG per bench at
   `engine/reports/_summary/by_bench/<Bench>/comparison.svg`. One band per
   method, two bars per band. Methods sorted by random's coverage descending.

3. **Per-bench averages** — `engine/reports/_summary/suite.svg`. Vertical
   grouped bars: one group per bench, one bar per strategy per group. Bar
   height = aggregate `Σ covered / Σ total` across the bench's methods.

4. **Suite-wide overall** — `engine/reports/_summary/overall.svg`. Vertical
   single-group bars: one bar per strategy, aggregated across every method.

5. **Markdown table** at `engine/reports/_summary/comparison.md`, one row per
   (Bench, method) with the precise numbers behind the charts.

The script is pure post-processing; it never invokes sbt or the engine. Run
`make compare` (which `make all` also depends on) after `make run`. The
script lives under `docs/diagrams/` alongside the architecture diagram
scripts so that every Python+matplotlib output in the repo lives in one
place, but unlike them it reads runtime output, not source.
"""

from __future__ import annotations

import json
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import matplotlib.pyplot as plt
import numpy as np

# ── Locations ──────────────────────────────────────────────────────────
# This script lives under `docs/diagrams/` alongside the architecture diagram
# scripts, but it reads engine *runtime* output (per-strategy `coverage.json`)
# and writes the comparison artefacts back next to those reports, not under
# `docs/images/`. Hence the explicit hop up to the repo root.
REPO_ROOT = Path(__file__).resolve().parent.parent.parent
REPORTS_DIR = REPO_ROOT / "engine" / "reports"
SUMMARY_DIR = REPORTS_DIR / "_summary"

# Canonical strategy order — matches `Strategy.names` in the Scala engine and
# `STRATEGIES` in the Makefile. Anything not in this list is ignored.
STRATEGIES: List[str] = ["random", "mutation-guided"]

# Pastel palette shared with `docs/diagrams/shapes.py` for visual coherence.
COLOR: Dict[str, str] = {
    "random":          "#90CAF9",
    "mutation-guided": "#A5D6A7",
}
LABEL: Dict[str, str] = {
    "random":          "Random",
    "mutation-guided": "Mutation-guided",
}
STROKE = "#37474F"
TEXT = "#263238"
GRID = "#ECEFF1"


# ── Data model ────────────────────────────────────────────────────────
@dataclass(frozen=True)
class Report:
    bench: str
    method: str
    strategy: str
    total: int
    covered: int
    saturation: Optional[int]
    growth: Tuple[int, ...]

    @property
    def coverage_pct(self) -> float:
        return (self.covered / self.total * 100.0) if self.total > 0 else 0.0

    def first_input_reaching(self, target: int) -> Optional[int]:
        """First index `i` where `growth[i] >= target`, or `None` if never."""
        for i, v in enumerate(self.growth):
            if v >= target:
                return i
        return None


def load_reports() -> Dict[Tuple[str, str, str], Report]:
    """Walk `<Bench>/<method>/<strategy>/data/coverage.json` files."""
    out: Dict[Tuple[str, str, str], Report] = {}
    for j in sorted(REPORTS_DIR.rglob("data/coverage.json")):
        rel = j.relative_to(REPORTS_DIR).parts
        # Expected: (bench, method, strategy, "data", "coverage.json").
        if len(rel) != 5 or rel[0].startswith("_"):
            continue
        bench, method, strategy = rel[0], rel[1], rel[2]
        if strategy not in STRATEGIES:
            continue
        with j.open(encoding="utf-8") as f:
            d = json.load(f)
        out[(bench, method, strategy)] = Report(
            bench=bench,
            method=method,
            strategy=strategy,
            total=d["sourceBranches"]["total"],
            covered=d["sourceBranches"]["covered"],
            saturation=d.get("saturationInputIndex"),
            growth=tuple(d["growthCurve"]),
        )
    return out


def group_by_method(reports: Dict[Tuple[str, str, str], Report]
                    ) -> Dict[Tuple[str, str], Dict[str, Report]]:
    """Index reports by `(bench, method)` for per-method chart generation."""
    groups: Dict[Tuple[str, str], Dict[str, Report]] = defaultdict(dict)
    for (b, m, s), r in reports.items():
        groups[(b, m)][s] = r
    return groups


# ── Plot helpers ───────────────────────────────────────────────────────
def style_axes(ax) -> None:
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    for spine in ("left", "bottom"):
        ax.spines[spine].set_color(STROKE)
    ax.tick_params(colors=TEXT, length=4)


def save_svg(fig, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(out, format="svg", bbox_inches="tight", facecolor="white")
    plt.close(fig)
    print(f"wrote {out.relative_to(REPORTS_DIR.parent)}")


# ── Combined value label ───────────────────────────────────────────────
def _combined_label(strategy: str, r: Report,
                    by_s: Dict[str, Report]) -> str:
    """The single text element drawn next to a strategy's bar.

    Always carries `cov%  (covered/total)  ·  sat #N`. For a *guided*
    strategy that strictly beat random's coverage we also append
    `·  → random at #t` — the input at which the guided strategy first
    matched random's covered count.

    The addendum is *suppressed* when `t == saturation`: the `sat #N` slot
    already shows that number, so a second copy would just clutter the row.
    Random rows never get the addendum (it would be self-referential).
    """
    base = f"{r.coverage_pct:.0f}%  ({r.covered}/{r.total})"
    sat = f"sat #{r.saturation}" if r.saturation is not None else "sat —"
    label = f"{base}  ·  {sat}"

    if strategy == "random":
        return label
    random_r = by_s.get("random")
    if random_r is None or random_r.covered == 0:
        return label
    if r.covered <= random_r.covered:  # strict beat only
        return label
    t = r.first_input_reaching(random_r.covered)
    if t is None or t == r.saturation:
        return label
    return f"{label}  ·  → random at #{t}"


# ── Horizontal chart: per-method and per-bench share this shape ───────
def _strategy_chart(
    items: List[Tuple[Tuple[str, str], Dict[str, Report]]],
    out: Path,
    title: str,
    figsize: Optional[Tuple[float, float]] = None,
) -> None:
    """Horizontal grouped bars: one band per method, one bar per strategy.

    Bar length = coverage %. The right-of-bar text is the single combined
    label produced by `_combined_label` — it packs coverage, plateau and
    (when guided beat random) the speed-to-random number into one slot.
    `figsize=None` picks a height that scales with the number of methods.
    """
    if not items:
        return

    bar_h = 0.30          # one bar's thickness, in data units
    inner_gap = 0.04      # gap between adjacent bars within a band
    outer_gap = 0.55      # gap between consecutive bands
    xlim = (0.0, 165.0)   # accommodates the longest combined label

    n_strats = len(STRATEGIES)
    band_h = n_strats * bar_h + (n_strats - 1) * inner_gap

    band_top: List[float] = []
    y = 0.0
    for _ in items:
        band_top.append(y)
        y += band_h + outer_gap

    total_data_h = y - outer_gap
    if figsize is None:
        # Default: ~0.55 inches per band, with a floor for small benches.
        figsize = (11, max(2.6, total_data_h * 0.55 + 1.6))

    fig, ax = plt.subplots(figsize=figsize)

    y_ticks: List[float] = []
    y_labels: List[str] = []

    for ((_, method), by_s), bt in zip(items, band_top):
        for j, s in enumerate(STRATEGIES):
            r = by_s.get(s)
            if r is None:
                continue
            y = bt + j * (bar_h + inner_gap) + bar_h / 2
            cov = r.coverage_pct
            ax.barh(y, cov, height=bar_h, color=COLOR[s],
                    edgecolor=STROKE, linewidth=0.9, zorder=2)
            ax.text(cov + xlim[1] * 0.006, y, _combined_label(s, r, by_s),
                    va="center", ha="left", fontsize=8, color=TEXT)

        y_ticks.append(bt + band_h / 2)
        y_labels.append(method)

    ax.set_yticks(y_ticks)
    ax.set_yticklabels(y_labels, fontsize=9, color=TEXT)
    ax.set_xlim(*xlim)
    ax.set_ylim(-band_h * 0.5, total_data_h + band_h * 0.5)
    ax.invert_yaxis()
    ax.set_xlabel("Coverage reached  (%)", fontsize=10, color=TEXT)
    ax.set_title(title, fontsize=13, fontweight="bold", color=TEXT,
                 loc="left", pad=12)
    ax.grid(axis="x", color=GRID, linewidth=0.7, zorder=0)
    style_axes(ax)

    legend_handles = [plt.Rectangle((0, 0), 1, 1, facecolor=COLOR[s],
                                     edgecolor=STROKE, linewidth=0.9)
                      for s in STRATEGIES]
    legend_labels = [LABEL[s] for s in STRATEGIES]
    # Anchor scales with figure height so the legend sits below the xlabel on
    # both the tiny per-method chart (fig_h ≈ 3) and the tall per-bench one
    # (fig_h ≈ 13); plain matplotlib axes coordinates don't give us that
    # automatically.
    ax.legend(legend_handles, legend_labels, loc="upper center",
              bbox_to_anchor=(0.5, -1.0 / figsize[1]),
              ncol=len(legend_labels), frameon=False, fontsize=9)

    plt.tight_layout()
    save_svg(fig, out)


def _sort_methods(items: List[Tuple[Tuple[str, str], Dict[str, Report]]],
                  by_strategy: str = "random"
                  ) -> List[Tuple[Tuple[str, str], Dict[str, Report]]]:
    """Sort by `by_strategy`'s coverage descending, then by method name."""

    def key(item):
        (_, m), by_s = item
        r = by_s.get(by_strategy)
        cov = r.coverage_pct if r is not None else 0.0
        return (-cov, m)

    return sorted(items, key=key)


def per_method_chart(bench: str, method: str,
                     by_strategy: Dict[str, Report], out: Path) -> None:
    """Single horizontal chart for one (bench, method) — two bars total."""
    if "random" not in by_strategy:
        # Without the random baseline the `→ random` addendum can't be
        # computed, and the chart would carry only half its intended story.
        return
    _strategy_chart(
        items=[((bench, method), by_strategy)],
        out=out,
        title=f"{bench} · {method}",
        # 3.2" gives the xlabel and legend their own line below the bars
        # without overlapping each other (legend bbox is height-scaled).
        figsize=(11, 3.2),
    )


def per_bench_charts(grouped: Dict[Tuple[str, str], Dict[str, Report]],
                     out_dir: Path) -> None:
    by_bench: Dict[str, List[Tuple[Tuple[str, str], Dict[str, Report]]]] = defaultdict(list)
    for key, by_s in grouped.items():
        by_bench[key[0]].append((key, by_s))

    for bench, items in sorted(by_bench.items()):
        items = _sort_methods(items)
        _strategy_chart(
            items=items,
            out=out_dir / bench / "comparison.svg",
            title=f"{bench} — coverage by strategy",
        )


# ── Aggregate (per-bench-average + suite-overall) charts ──────────────
#
# The per-bench / per-method views above answer "which methods differentiate
# the strategies?". These two answer "across an entire class of tests, and
# across the whole suite, who wins?" — the thesis-headline numbers.
#
# Aggregation is *branch-weighted*: `Σ covered / Σ total branches`, not
# per-method-equal-weight. A 12-branch method counts more than a 2-branch
# method, which matches the natural "how much of the SUT did we cover?"
# reading. Saturation is intentionally not aggregated here — averaging
# "first input that plateaued" across two- and twenty-branch methods doesn't
# compare like with like; per-method saturation lives in the bar labels of
# the per-method and per-bench views above.


def _aggregate_coverage_by_bench(reports: Dict[Tuple[str, str, str], Report],
                                 benches: List[str]
                                 ) -> List[Tuple[str, Dict[str, float]]]:
    out: List[Tuple[str, Dict[str, float]]] = []
    for bench in benches:
        bucket: Dict[str, List[int]] = defaultdict(lambda: [0, 0])
        for (b, _, s), r in reports.items():
            if b != bench:
                continue
            bucket[s][0] += r.covered
            bucket[s][1] += r.total
        out.append((bench, {s: (bucket[s][0] / bucket[s][1] * 100.0
                                if bucket[s][1] > 0 else 0.0)
                            for s in STRATEGIES}))
    return out


def _overall_coverage(reports: Dict[Tuple[str, str, str], Report]
                      ) -> Dict[str, float]:
    bucket: Dict[str, List[int]] = defaultdict(lambda: [0, 0])
    for (_, _, s), r in reports.items():
        bucket[s][0] += r.covered
        bucket[s][1] += r.total
    return {s: (bucket[s][0] / bucket[s][1] * 100.0
                if bucket[s][1] > 0 else 0.0) for s in STRATEGIES}


def suite_chart(reports: Dict[Tuple[str, str, str], Report],
                benches: List[str], out_dir: Path) -> None:
    """Per-bench aggregate coverage as a vertical grouped-bar chart.

    Bar height = aggregate `Σ covered / Σ total` across the bench's methods.
    Saturation is *not* aggregated here: the "mean first-input-to-plateau"
    across methods with wildly different branch counts isn't a meaningful
    comparison number, so we keep that detail at the per-bench / per-method
    level and let the suite chart stay a single clean headline.
    """
    coverage = _aggregate_coverage_by_bench(reports, benches)

    n_groups = len(coverage)
    bar_w = 0.30
    x = np.arange(n_groups)

    fig, ax = plt.subplots(figsize=(10, 5.2))
    style_axes(ax)

    max_cov = max((v for _, d in coverage for v in d.values()), default=0.0)
    head_room = max(max_cov * 1.20, 1.0)

    for i, s in enumerate(STRATEGIES):
        cov_vals = [d.get(s, 0.0) for _, d in coverage]
        offset = (i - (len(STRATEGIES) - 1) / 2.0) * bar_w
        ax.bar(x + offset, cov_vals, width=bar_w,
               color=COLOR[s], edgecolor=STROKE, linewidth=1.1,
               label=LABEL[s], zorder=2)
        for bx, cov in zip(x + offset, cov_vals):
            ax.text(bx, cov + max_cov * 0.018, f"{cov:.0f}%",
                    ha="center", va="bottom", fontsize=10, color=TEXT)

    ax.set_xticks(x)
    ax.set_xticklabels([b for b, _ in coverage], fontsize=11, color=TEXT)
    ax.set_ylabel("Coverage  (Σ covered / Σ total branches, %)",
                  fontsize=11, color=TEXT)
    ax.set_ylim(0, head_room)
    ax.set_title("Per-bench coverage by strategy",
                 fontsize=13, fontweight="bold", color=TEXT,
                 loc="left", pad=14)
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.10),
              ncol=len(STRATEGIES), frameon=False, fontsize=10)
    ax.grid(axis="y", color=GRID, linewidth=0.7, zorder=0)

    plt.tight_layout()
    save_svg(fig, out_dir / "suite.svg")


def overall_chart(reports: Dict[Tuple[str, str, str], Report],
                  out_dir: Path) -> None:
    """Suite-wide aggregate coverage: one bar per strategy."""
    cov = _overall_coverage(reports)
    cov_vals = [cov.get(s, 0.0) for s in STRATEGIES]

    fig, ax = plt.subplots(figsize=(7.5, 4.8))
    style_axes(ax)

    max_cov = max(cov_vals + [1.0])
    head_room = max_cov * 1.22

    x = np.arange(len(STRATEGIES))
    ax.bar(x, cov_vals, width=0.52,
           color=[COLOR[s] for s in STRATEGIES],
           edgecolor=STROKE, linewidth=1.2, zorder=2)
    for bx, cov_v in zip(x, cov_vals):
        ax.text(bx, cov_v + max_cov * 0.022, f"{cov_v:.1f}%",
                ha="center", va="bottom", fontsize=12,
                color=TEXT, fontweight="bold")

    ax.set_xticks(x)
    ax.set_xticklabels([LABEL[s] for s in STRATEGIES],
                       fontsize=11, color=TEXT)
    ax.set_ylabel("Coverage  (Σ covered / Σ total branches, %)",
                  fontsize=11, color=TEXT)
    ax.set_ylim(0, head_room)
    ax.set_title("Suite-wide coverage by strategy",
                 fontsize=13, fontweight="bold", color=TEXT,
                 loc="left", pad=14)
    ax.grid(axis="y", color=GRID, linewidth=0.7, zorder=0)

    plt.tight_layout()
    save_svg(fig, out_dir / "overall.svg")


# ── Markdown table ────────────────────────────────────────────────────
def markdown_table(grouped: Dict[Tuple[str, str], Dict[str, Report]],
                   out: Path) -> None:
    """One row per (Bench, method) with cov/sat/→R for every strategy."""

    def cell(r: Optional[Report], target_cov: Optional[int] = None) -> str:
        if r is None:
            return "—"
        sat = f"#{r.saturation}" if r.saturation is not None else "—"
        base = f"{r.covered}/{r.total} ({r.coverage_pct:.0f}%) · sat {sat}"
        if target_cov is None or target_cov <= 0:
            # Either this is the random row, or random itself covered nothing
            # (degenerate 0/0 method) — `→R` carries no information.
            return base
        if r.covered < target_cov:
            return base + " · →R —"
        t = r.first_input_reaching(target_cov)
        return base + (f" · →R #{t}" if t is not None else " · →R —")

    lines = [
        "# Strategy comparison",
        "",
        "Each row: one (Bench, method). `cov/total (%)` = final coverage. "
        "`sat #n` = first input at which the strategy plateaued (i.e. reached "
        "its own maximum coverage). `→R #n` = first input at which a guided "
        "strategy matched random's covered count (— if it never did, or if "
        "random itself covered nothing).",
        "",
        "| Bench | Method | Total | Random | Mutation-guided |",
        "|-------|--------|------:|--------|-----------------|",
    ]
    for (b, m), by_s in sorted(grouped.items()):
        random_r = by_s.get("random")
        if random_r is None:
            continue
        row = [
            b, m, str(random_r.total),
            cell(random_r),
            cell(by_s.get("mutation-guided"), target_cov=random_r.covered),
        ]
        lines.append("| " + " | ".join(row) + " |")

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {out.relative_to(REPORTS_DIR.parent)}")


# ── Entry point ───────────────────────────────────────────────────────
def main() -> None:
    reports = load_reports()
    if not reports:
        print(f"No reports found under {REPORTS_DIR}. Run `make run` first.")
        return

    grouped = group_by_method(reports)

    for (bench, method), by_s in grouped.items():
        per_method_chart(bench, method, by_s,
                         REPORTS_DIR / bench / method / "comparison.svg")

    per_bench_charts(grouped, SUMMARY_DIR / "by_bench")

    benches = sorted({b for (b, _) in grouped.keys()})
    suite_chart(reports, benches, SUMMARY_DIR)
    overall_chart(reports, SUMMARY_DIR)

    markdown_table(grouped, SUMMARY_DIR / "comparison.md")


if __name__ == "__main__":
    main()
