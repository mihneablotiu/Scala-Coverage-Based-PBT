"""Hexagonal (ports-and-adapters) architecture — conceptual view.

Shows the pattern, not the technology. Driven ports use role-based labels
(``Coverage Tracker`` rather than ``JacocoBranchCoverageTracker``); driven
adapters are labelled by what they *do* (``Probes``, ``Parser``…) without
naming the concrete library. The mapping to real classes lives in the
prose under ``docs/architecture.md``.

Run: ``python3 docs/diagrams/hexagon.py``.
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(__file__))

from shapes import (  # noqa: E402
    COLOR_ADAPTER,
    COLOR_DOMAIN,
    COLOR_PORT,
    COLOR_SOFT,
    arrow,
    canvas,
    column,
    labeled_box,
    save,
    title as draw_title,
)

# ── Canvas & geometry ─────────────────────────────────────────────────
W, H = 24.0, 14.0
fig, ax = canvas(W, H)

COL_Y      = 0.6
COL_H      = 12.4
HEADER_Y   = COL_Y + COL_H - 0.6   # used to align column-header labels

# Column geometry: (x0, width, header)
COLS = [
    (0.5,  3.4, "Driving adapters"),
    (4.3,  3.0, "Driving ports"),
    (7.7,  8.0, "Use case"),
    (16.1, 4.2, "Driven ports"),
    (20.7, 2.8, "Driven adapters"),
]

for x0, w, header in COLS:
    column(ax, x0, COL_Y, w, COL_H, header, label_size=13)

# ── Vertical layout for the 5 driven-port rows ─────────────────────────
ROW_Y = [10.30, 8.40, 6.50, 4.60, 2.70]   # box bottoms
BOX_H = 1.40

# ── Driving side ──────────────────────────────────────────────────────
labeled_box(
    ax, 0.95, 6.10, 2.5, 1.6,
    title="Application",
    subtitle="entry point",
    fill=COLOR_ADAPTER,
)

labeled_box(
    ax, 4.75, 6.20, 2.1, 1.4,
    title="Test Runner",
    fill=COLOR_PORT,
)

# ── Use case + Domain ─────────────────────────────────────────────────
# Italic subtitle below the column header.
ax.text(11.7, 11.5, "orchestrates the fuzz session",
        ha="center", va="center", fontsize=11,
        style="italic", color=COLOR_SOFT)

# Domain box — fills most of the Use-case column.
labeled_box(
    ax, 8.6, 1.8, 6.2, 8.8,
    title="", fill=COLOR_DOMAIN, radius=0.28,
)
ax.text(11.7, 9.65, "Domain", ha="center", va="center",
        fontsize=20, fontweight="bold")

DOMAIN_LINES = [
    "Fuzz session",
    "Branch tree",
    "Coverage snapshot",
    "Input history",
    "Growth curve",
]
y0 = 8.45
for i, line in enumerate(DOMAIN_LINES):
    ax.text(11.7, y0 - i * 1.05, line,
            ha="center", va="center", fontsize=13.5,
            color="#1A1A1A")

# ── Driven ports (5 rows of role-based labels) ────────────────────────
PORT_LABELS = [
    "Input Generator",
    "Coverage Tracker",
    "Tree Builder",
    "Coverage Reader",
    "Report Writer",
]
PORT_W = 3.6
PORT_X = 16.4

for y, label in zip(ROW_Y, PORT_LABELS):
    labeled_box(ax, PORT_X, y, PORT_W, BOX_H, title=label,
                fill=COLOR_PORT, title_size=12.5)

# ── Driven adapters (one per port — Input Generator stacks two) ────────
ADAPTER_X = 21.0
ADAPTER_W = 2.4

# Input Generator slot: two adapters stacked vertically.
ig_y = ROW_Y[0]
labeled_box(ax, ADAPTER_X, ig_y + 0.78, ADAPTER_W, 0.6, "Random", fill=COLOR_ADAPTER, title_size=11)
labeled_box(ax, ADAPTER_X, ig_y + 0.05, ADAPTER_W, 0.6, "Guided", fill=COLOR_ADAPTER, title_size=11)

OTHER_ADAPTERS = [
    ("Probes",   ROW_Y[1]),
    ("Parser",   ROW_Y[2]),
    ("Snapshot", ROW_Y[3]),
    ("Writer",   ROW_Y[4]),
]
for lbl, y in OTHER_ADAPTERS:
    labeled_box(ax, ADAPTER_X, y, ADAPTER_W, BOX_H, title=lbl,
                fill=COLOR_ADAPTER, title_size=12)

# ── Arrows ─────────────────────────────────────────────────────────────
# Driving side flows right: adapter → port → use case
arrow(ax, 3.45, 6.90, 4.75, 6.90)
arrow(ax, 6.85, 6.90, 8.00, 6.90)

# Use case (domain) → driven port: arrows pointing right from the domain edge
for y in ROW_Y:
    arrow(ax, 14.85, y + BOX_H / 2, 16.40, y + BOX_H / 2)

# Driven adapter → driven port: arrows pointing left into the port (adapter implements port)
# Other adapters: single horizontal arrow per row
for _, y in OTHER_ADAPTERS:
    arrow(ax, 21.0, y + BOX_H / 2, 20.00, y + BOX_H / 2, color=COLOR_SOFT, lw=1.5)

# InputGenerator slot: both adapters share the same port.
arrow(ax, 21.0, ig_y + 1.08, 20.00, ig_y + BOX_H / 2 + 0.10, color=COLOR_SOFT, lw=1.5)
arrow(ax, 21.0, ig_y + 0.35, 20.00, ig_y + BOX_H / 2 - 0.10, color=COLOR_SOFT, lw=1.5)

# ── Title ──────────────────────────────────────────────────────────────
draw_title(ax, W / 2, H - 0.3, "Hexagonal Architecture", fontsize=20)

save(fig, "hexagon")
