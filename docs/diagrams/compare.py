#!/usr/bin/env python3
"""Strategy comparison artifacts from per-strategy coverage JSON.

Reads `engine/reports/<Bench>/<method>/<strategy>/data/coverage.json` for every
strategy and writes three flavours of comparison artifacts:

1. **Per-method** — one SVG per (Bench, method) at
   `engine/reports/<Bench>/<method>/comparison.svg`. Two stacked panels:
     - top: max coverage reached (%) per strategy.
     - bottom: input index at which each strategy plateaued; a small "○"
       marker on each guided-strategy row shows the first input at which it
       matched random's covered count (so you can tell whether a guided
       strategy reached random's plateau faster, even if their final
       coverages eventually differ).

2. **Per-bench detailed comparisons** — two horizontal grouped-bar SVGs per
   bench, under `engine/reports/_summary/by_bench/<Bench>/`:
     - `coverage.svg`: one row per method, three bars per row (R / M / F),
       x = max coverage %. Methods sorted by random's coverage descending
       (best storytelling: easy wins at top, harder methods at bottom).
     - `plateau.svg`: same layout, x = saturation input. A small ○ marker
       on guided rows when the guided strategy went *above* random's
       coverage — it sits at the first input at which it matched random's
       ceiling, so you can read off both "did guided plateau faster?" and
       "did guided reach random's ceiling sooner than random did?" in one
       glance.

3. **Suite-wide deep-view** — two horizontal SVGs under
   `engine/reports/_summary/`:
     - `suite_coverage.svg`: every method in the suite, grouped by bench
       with thin separators, three bars per method.
     - `suite_plateau.svg`: same structure for plateau inputs.

4. **Markdown table** at `engine/reports/_summary/comparison.md` listing every
   (Bench, method) row with the precise numbers behind the charts.

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
from typing import Callable, Dict, List, Optional, Tuple

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

# Canonical strategy order — matches `Strategy.all` in the Scala engine and
# `STRATEGIES` in the Makefile. Anything not in this list is ignored.
STRATEGIES: List[str] = ["random", "mutation-guided", "feedback-bias-guided"]

# Pastel palette shared with `docs/diagrams/shapes.py` for visual coherence.
COLOR: Dict[str, str] = {
    "random":               "#90CAF9",
    "mutation-guided":      "#A5D6A7",
    "feedback-bias-guided": "#CE93D8",
}
LABEL: Dict[str, str] = {
    "random":               "Random",
    "mutation-guided":      "Mutation-guided",
    "feedback-bias-guided": "Feedback-bias-guided",
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


# ── Per-method chart ──────────────────────────────────────────────────
def per_method_chart(bench: str, method: str,
                     by_strategy: Dict[str, Report], out: Path) -> None:
    """Two-panel SVG: coverage bars + saturation-input bars with markers."""
    random_r = by_strategy.get("random")
    if random_r is None:
        # Without random as the baseline there is no "time to random's plateau"
        # comparison to draw — skip silently.
        return

    fig, (ax_cov, ax_inp) = plt.subplots(
        2, 1, figsize=(9, 4.5),
        gridspec_kw={"height_ratios": [1, 1.15]},
    )

    names = [LABEL[s] for s in STRATEGIES]
    colors = [COLOR[s] for s in STRATEGIES]
    y_pos = np.arange(len(STRATEGIES))

    # ── Top panel: max coverage % ──────────────────────────────────────
    pcts = [by_strategy[s].coverage_pct if s in by_strategy else 0.0 for s in STRATEGIES]
    ax_cov.barh(y_pos, pcts, color=colors, edgecolor=STROKE, linewidth=1.1, zorder=2)
    for y, p, s in zip(y_pos, pcts, STRATEGIES):
        r = by_strategy.get(s)
        suffix = f"  ({r.covered}/{r.total})" if r else ""
        ax_cov.text(p + 1.5, y, f"{p:.0f}%{suffix}", va="center", fontsize=10, color=TEXT)
    ax_cov.set_yticks(y_pos)
    ax_cov.set_yticklabels(names, fontsize=10, color=TEXT)
    ax_cov.set_xlim(0, 118)
    ax_cov.set_xlabel("Maximum coverage reached  (%)", fontsize=10, color=TEXT)
    ax_cov.invert_yaxis()
    ax_cov.grid(axis="x", color=GRID, linewidth=0.7, zorder=0)
    style_axes(ax_cov)
    ax_cov.set_title(f"{bench} · {method}", fontsize=13, fontweight="bold",
                     color=TEXT, loc="left", pad=10)

    # ── Bottom panel: inputs to plateau + cross-strategy markers ───────
    sats: List[int] = []
    for s in STRATEGIES:
        r = by_strategy.get(s)
        if r is None or r.saturation is None:
            sats.append(0)
        else:
            sats.append(r.saturation)

    ax_inp.barh(y_pos, sats, color=colors, edgecolor=STROKE, linewidth=1.1, zorder=2)
    for y, sat, s in zip(y_pos, sats, STRATEGIES):
        r = by_strategy.get(s)
        if r is None or r.covered == 0:
            ax_inp.text(2, y, "(no coverage)", va="center",
                        fontsize=9, color=STROKE, style="italic")
        else:
            ax_inp.text(sat + 1.5, y, f"#{sat}", va="center", fontsize=10, color=TEXT)

    # "Time to random's plateau" marker on guided rows when applicable.
    random_cov = random_r.covered
    for s, y in zip(STRATEGIES, y_pos):
        if s == "random":
            continue
        r = by_strategy.get(s)
        if r is None or random_cov == 0:
            continue
        if r.covered < random_cov:
            ax_inp.text(2, y - 0.32, "(did not reach random's coverage)",
                        fontsize=8, color=STROKE, style="italic", va="center")
            continue
        t = r.first_input_reaching(random_cov)
        if t is None or t == r.saturation:
            # If the guided strategy reached random's coverage at exactly its own
            # plateau input, the bar already marks it — no extra ○ needed.
            continue
        ax_inp.plot(t, y, marker="o", markersize=9, markerfacecolor="white",
                    markeredgecolor=STROKE, markeredgewidth=1.5, zorder=10)
        ax_inp.text(t + 1.5, y - 0.32,
                    f"matched random's {random_cov}/{r.total} at input #{t}",
                    fontsize=8, color=STROKE, style="italic", va="center")

    ax_inp.set_yticks(y_pos)
    ax_inp.set_yticklabels(names, fontsize=10, color=TEXT)
    ax_inp.set_xlim(0, max(105, max(sats) + 15))
    ax_inp.set_xlabel(
        "Input index at which the strategy plateaued"
        "   (○ = first input at which a guided strategy matched random's coverage)",
        fontsize=9, color=TEXT,
    )
    ax_inp.invert_yaxis()
    ax_inp.grid(axis="x", color=GRID, linewidth=0.7, zorder=0)
    style_axes(ax_inp)

    plt.tight_layout()
    save_svg(fig, out)


# ── Multi-method horizontal grouped-bar charts ─────────────────────────
#
# Every chart below shares the same shape: one row per method, three bars
# per row (random / mutation-guided / feedback-bias-guided), x-axis = the
# metric of interest. Methods are sorted by random's value descending so
# the storytelling cascades from "everyone aces this" at the top to
# "interesting differentiation happens here" at the bottom — the order
# a thesis figure wants to draw the eye towards.

def _sort_methods(items: List[Tuple[Tuple[str, str], Dict[str, Report]]],
                  by_strategy: str = "random") -> List[Tuple[Tuple[str, str], Dict[str, Report]]]:
    """Sort by `by_strategy`'s coverage descending, then by method name ascending."""

    def key(item):
        (_, m), by_s = item
        r = by_s.get(by_strategy)
        cov = r.coverage_pct if r is not None else 0.0
        return (-cov, m)

    return sorted(items, key=key)


def _bench_separators(items: List[Tuple[Tuple[str, str], Dict[str, Report]]]) -> List[int]:
    """Indices in `items` at which the bench label changes (excluding 0)."""
    seps: List[int] = []
    last = None
    for i, ((b, _), _) in enumerate(items):
        if last is not None and b != last:
            seps.append(i)
        last = b
    return seps


def _strategy_chart(
    items: List[Tuple[Tuple[str, str], Dict[str, Report]]],
    extract: Callable[[Report], float],
    value_label: Callable[[Report], str],
    xlabel: str,
    title: str,
    xlim: Tuple[float, float],
    out: Path,
    marker_fn: Optional[Callable[[str, Report, Dict[str, Report]],
                                  Optional[Tuple[int, str]]]] = None,
    show_bench_headers: bool = False,
) -> None:
    """Horizontal grouped bars: one band per method, three bars per band.

    `marker_fn(strategy, report, by_strategy)` may return `(x, text)` to overlay
    a ○-and-label marker on a guided bar — used by the plateau chart to show
    "matched random's coverage at input #N" when the guided strategy went
    above random's ceiling.

    When `show_bench_headers` is set (the suite chart) each new bench gets a
    horizontal banner above its first method, with the bench name in bold —
    the same convention thesis figures use when grouping rows by category.
    """
    if not items:
        return

    bar_h = 0.30          # one bar's thickness, in data units
    inner_gap = 0.04      # gap between the 3 bars within a band
    outer_gap = 0.55      # gap between consecutive bands of the same bench
    header_gap = 1.20     # extra gap before a bench header (only used when banners are on)

    band_h = 3 * bar_h + 2 * inner_gap

    # Pre-compute each band's top-y. When bench headers are shown we add
    # `header_gap` of vertical space before the first band of every bench
    # (including the very first) to make room for the banner text.
    band_top: List[float] = []
    y = 0.0
    last_bench: Optional[str] = None
    for ((bench, _), _) in items:
        if show_bench_headers and bench != last_bench:
            y += header_gap
            last_bench = bench
        band_top.append(y)
        y += band_h + outer_gap

    total_data_h = y - outer_gap  # actual data extent
    # Figure height — roughly 0.55 inches per band, with a floor for tiny benches.
    fig_h = max(2.6, total_data_h * 0.55 + 1.6)

    fig, ax = plt.subplots(figsize=(11, fig_h))

    y_ticks: List[float] = []
    y_labels: List[str] = []

    for i, (((bench, method), by_s), bt) in enumerate(zip(items, band_top)):
        for j, s in enumerate(STRATEGIES):
            r = by_s.get(s)
            if r is None:
                continue
            y = bt + j * (bar_h + inner_gap) + bar_h / 2
            v = extract(r)
            ax.barh(y, v, height=bar_h, color=COLOR[s],
                    edgecolor=STROKE, linewidth=0.9, zorder=2)
            ax.text(v + xlim[1] * 0.006, y, value_label(r),
                    va="center", ha="left", fontsize=8, color=TEXT)
            if marker_fn is not None and s != "random":
                marker = marker_fn(s, r, by_s)
                if marker is not None:
                    mx, mtext = marker
                    ax.plot(mx, y, marker="o", markersize=7,
                            markerfacecolor="white", markeredgecolor=STROKE,
                            markeredgewidth=1.3, zorder=10)
                    if mtext:
                        ax.text(mx + xlim[1] * 0.006, y, mtext,
                                va="center", ha="left", fontsize=7.5,
                                style="italic", color=STROKE)

        y_ticks.append(bt + band_h / 2)
        y_labels.append(method)

    ax.set_yticks(y_ticks)
    ax.set_yticklabels(y_labels, fontsize=9, color=TEXT)
    ax.set_xlim(*xlim)
    ax.set_ylim(-band_h * 0.5, total_data_h + band_h * 0.5)
    ax.invert_yaxis()
    ax.set_xlabel(xlabel, fontsize=10, color=TEXT)
    ax.set_title(title, fontsize=13, fontweight="bold", color=TEXT, loc="left", pad=12)
    ax.grid(axis="x", color=GRID, linewidth=0.7, zorder=0)
    style_axes(ax)

    # Bench banners + thin separator lines on the suite chart.
    if show_bench_headers:
        bench_first_idx: Dict[str, int] = {}
        for i, ((b, _), _) in enumerate(items):
            bench_first_idx.setdefault(b, i)
        for bench, idx in bench_first_idx.items():
            header_y = band_top[idx] - header_gap * 0.55
            ax.text(xlim[0] + (xlim[1] - xlim[0]) * 0.005, header_y, bench,
                    ha="left", va="center", fontsize=11, fontweight="bold",
                    color=TEXT,
                    bbox=dict(boxstyle="round,pad=0.32",
                              facecolor="#ECEFF1", edgecolor=STROKE,
                              linewidth=0.6))
            if idx > 0:
                sep_y = band_top[idx] - header_gap * 0.95
                ax.axhline(sep_y, color=STROKE, linewidth=0.7, alpha=0.35, zorder=1)

    # Legend below the chart. Offset is tuned to scale roughly inversely with
    # figure height so tall charts don't push the legend off the page.
    legend_handles = [plt.Rectangle((0, 0), 1, 1, facecolor=COLOR[s],
                                     edgecolor=STROKE, linewidth=0.9)
                      for s in STRATEGIES]
    legend_labels = [LABEL[s] for s in STRATEGIES]
    if marker_fn is not None:
        legend_handles.append(plt.Line2D([0], [0], marker="o", markersize=7,
                                          markerfacecolor="white",
                                          markeredgecolor=STROKE,
                                          markeredgewidth=1.3, linestyle=""))
        legend_labels.append("Guided ≥ random's ceiling, first match")
    ax.legend(legend_handles, legend_labels, loc="upper center",
              bbox_to_anchor=(0.5, -0.6 / fig_h), ncol=len(legend_labels),
              frameon=False, fontsize=9)

    plt.tight_layout()
    save_svg(fig, out)


def _coverage_value_label(r: Report) -> str:
    return f"{r.coverage_pct:.0f}%  ({r.covered}/{r.total})"


def _plateau_value_label(r: Report) -> str:
    return f"#{r.saturation}" if r.saturation is not None else "—"


def _plateau_marker(strategy: str, r: Report,
                    by_s: Dict[str, Report]) -> Optional[Tuple[int, str]]:
    """○ marker only when the guided strategy beat random's coverage.

    For ties (the common placeholder case today) and losses, the bar already
    tells the full story and an extra marker would just be noise.
    """
    random_r = by_s.get("random")
    if random_r is None or random_r.covered == 0:
        return None
    if r.covered <= random_r.covered:
        return None
    t = r.first_input_reaching(random_r.covered)
    if t is None:
        return None
    return t, f"  → matched random's {random_r.covered}/{r.total} at #{t}"


def per_bench_charts(grouped: Dict[Tuple[str, str], Dict[str, Report]],
                     out_dir: Path) -> None:
    by_bench: Dict[str, List[Tuple[Tuple[str, str], Dict[str, Report]]]] = defaultdict(list)
    for key, by_s in grouped.items():
        by_bench[key[0]].append((key, by_s))

    for bench, items in sorted(by_bench.items()):
        items = _sort_methods(items)
        max_sat = max(((r.saturation or 0) for _, by_s in items for r in by_s.values()),
                      default=0)
        bench_dir = out_dir / bench
        _strategy_chart(
            items=items,
            extract=lambda r: r.coverage_pct,
            value_label=_coverage_value_label,
            xlabel="Maximum coverage reached  (%)",
            title=f"{bench} — maximum coverage by strategy",
            xlim=(0, 118),
            out=bench_dir / "coverage.svg",
        )
        _strategy_chart(
            items=items,
            extract=lambda r: r.saturation if r.saturation is not None else 0,
            value_label=_plateau_value_label,
            xlabel="Input index at which the strategy plateaued",
            title=f"{bench} — plateau input by strategy",
            xlim=(0, max(105, max_sat + 18)),
            out=bench_dir / "plateau.svg",
            marker_fn=_plateau_marker,
        )


def suite_charts(grouped: Dict[Tuple[str, str], Dict[str, Report]],
                 out_dir: Path) -> None:
    # Group by bench, sort each bench's methods, then concatenate. Bench order
    # is canonical (alphabetical), which happens to match how the engine emits
    # them.
    by_bench: Dict[str, List[Tuple[Tuple[str, str], Dict[str, Report]]]] = defaultdict(list)
    for key, by_s in grouped.items():
        by_bench[key[0]].append((key, by_s))

    items: List[Tuple[Tuple[str, str], Dict[str, Report]]] = []
    for bench in sorted(by_bench.keys()):
        items.extend(_sort_methods(by_bench[bench]))

    max_sat = max(((r.saturation or 0) for _, by_s in items for r in by_s.values()),
                  default=0)

    _strategy_chart(
        items=items,
        extract=lambda r: r.coverage_pct,
        value_label=_coverage_value_label,
        xlabel="Maximum coverage reached  (%)",
        title="Suite — maximum coverage by strategy",
        xlim=(0, 118),
        out=out_dir / "suite_coverage.svg",
        show_bench_headers=True,
    )
    _strategy_chart(
        items=items,
        extract=lambda r: r.saturation if r.saturation is not None else 0,
        value_label=_plateau_value_label,
        xlabel="Input index at which the strategy plateaued",
        title="Suite — plateau input by strategy",
        xlim=(0, max(105, max_sat + 18)),
        out=out_dir / "suite_plateau.svg",
        marker_fn=_plateau_marker,
        show_bench_headers=True,
    )


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
        "> Until the guided strategies are implemented they delegate to random "
        "with the shared `Test.Parameters.withInitialSeed(0L)`, so every "
        "strategy generates the same input sequence and the three columns "
        "match exactly. They will start to diverge as soon as the placeholders "
        "in `usecase.strategy.{MutationGuidedGen, FeedbackBiasGuidedGen}` are "
        "replaced with real coverage-aware logic.",
        "",
        "| Bench | Method | Total | Random | Mutation-guided | Feedback-bias-guided |",
        "|-------|--------|------:|--------|-----------------|----------------------|",
    ]
    for (b, m), by_s in sorted(grouped.items()):
        random_r = by_s.get("random")
        if random_r is None:
            continue
        row = [
            b, m, str(random_r.total),
            cell(random_r),
            cell(by_s.get("mutation-guided"), target_cov=random_r.covered),
            cell(by_s.get("feedback-bias-guided"), target_cov=random_r.covered),
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
    suite_charts(grouped, SUMMARY_DIR)

    markdown_table(grouped, SUMMARY_DIR / "comparison.md")


if __name__ == "__main__":
    main()
